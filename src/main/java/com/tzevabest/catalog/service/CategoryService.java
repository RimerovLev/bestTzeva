package com.tzevabest.catalog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzevabest.catalog.domain.Category;
import com.tzevabest.catalog.domain.CategoryTranslation;
import com.tzevabest.catalog.dto.CategoryReorderRequest;
import com.tzevabest.catalog.dto.CategoryResponse;
import com.tzevabest.catalog.dto.CategorySaveRequest;
import com.tzevabest.catalog.dto.CategoryTranslationInput;
import com.tzevabest.catalog.dto.CategoryTranslationsRequest;
import com.tzevabest.catalog.dto.ReorderEntry;
import com.tzevabest.catalog.repository.CategoryRepository;
import com.tzevabest.catalog.repository.CategoryTranslationRepository;
import com.tzevabest.common.web.error.ConflictException;
import com.tzevabest.common.web.error.NotFoundException;
import com.tzevabest.common.web.error.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Управление категориями каталога: публичное дерево/список с локализацией и подсчётом активных
 * товаров (с учётом вложенных) и админский CRUD + переупорядочивание. Дерево кэшируется в Redis по
 * локали и инвалидируется при любом изменении. Глубина ограничена {@value #MAX_DEPTH} уровнями,
 * при смене родителя проверяется цикл.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    static final int MAX_DEPTH = 3;
    static final String DEFAULT_LOCALE = "he";
    private static final List<String> LOCALES = List.of("he", "ru", "en");
    private static final String CACHE_PREFIX = "catalog:cat:tree:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final CategoryRepository categories;
    private final CategoryTranslationRepository translations;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    // ── Публичное чтение ─────────────────────────────────────────────────────────

    /** Активные категории: дерево (roots с children) при {@code tree=true}, иначе плоский список. */
    public List<CategoryResponse> list(boolean tree, String localeParam) {
        List<CategoryResponse> roots = cachedTree(resolveLocale(localeParam));
        return tree ? roots : flatten(roots, new ArrayList<>());
    }

    /** Активная категория по slug со своим поддеревом. 404, если не найдена/неактивна. */
    public CategoryResponse getBySlug(String slug, String localeParam) {
        return findBySlug(cachedTree(resolveLocale(localeParam)), slug)
                .orElseThrow(() -> new NotFoundException("catalog.category.not_found"));
    }

    // ── Админ: CRUD ──────────────────────────────────────────────────────────────

    @Transactional
    public CategoryResponse create(CategorySaveRequest request) {
        Category parent = resolveParent(request.parentId());
        if (depthOf(parent) + 1 > MAX_DEPTH) {
            throw new ConflictException("catalog.category.max_depth");
        }
        Category category = new Category();
        category.setParent(parent);
        category.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        category.setActive(request.active() == null || request.active());
        category.setImageUrl(request.imageUrl());
        category.setSlug(resolveSlug(request.slug(), request.translations(), null));
        categories.save(category);
        saveTranslations(category, request.translations());
        invalidateCache();
        return adminView(category, request.translations());
    }

    @Transactional
    public CategoryResponse update(long id, CategorySaveRequest request) {
        Category category = categories.findById(id)
                .orElseThrow(() -> new NotFoundException("catalog.category.not_found"));

        Category newParent = resolveParent(request.parentId());
        checkNoCycle(category, newParent);
        checkDepthForMove(category, newParent);
        category.setParent(newParent);

        if (request.sortOrder() != null) {
            category.setSortOrder(request.sortOrder());
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }
        category.setImageUrl(request.imageUrl());
        if (request.slug() != null && !request.slug().isBlank()) {
            category.setSlug(resolveSlug(request.slug(), request.translations(), id));
        }

        translations.deleteByCategory_Id(id);
        translations.flush(); // до вставки новых, иначе конфликт uq (category_id, locale)
        saveTranslations(category, request.translations());
        categories.save(category);
        invalidateCache();
        return adminView(category, request.translations());
    }

    @Transactional
    public void delete(long id) {
        Category category = categories.findById(id)
                .orElseThrow(() -> new NotFoundException("catalog.category.not_found"));
        long products = categories.countProductsInCategory(id);
        long subcategories = categories.countByParent_Id(id);
        if (products > 0 || subcategories > 0) {
            throw new ConflictException("catalog.category.not_empty", products, subcategories);
        }
        categories.delete(category); // переводы удалятся каскадом FK
        invalidateCache();
    }

    /** Массовое изменение sort_order/parent_id. Валидирует всё дерево на цикл и глубину до записи. */
    @Transactional
    public void reorder(CategoryReorderRequest request) {
        Map<Long, Category> byId = new HashMap<>();
        for (Category c : categories.findAll()) {
            byId.put(c.getId(), c);
        }
        // Предлагаемая карта родителей: текущее состояние + переопределения из запроса.
        Map<Long, Long> parentOf = new HashMap<>();
        byId.forEach((cid, c) -> parentOf.put(cid, c.getParent() != null ? c.getParent().getId() : null));
        for (ReorderEntry item : request.items()) {
            if (!byId.containsKey(item.id())) {
                throw new NotFoundException("catalog.category.not_found");
            }
            if (item.parentId() != null && !byId.containsKey(item.parentId())) {
                throw new ValidationException("catalog.category.parent_not_found");
            }
            parentOf.put(item.id(), item.parentId());
        }
        validateForest(parentOf);

        for (ReorderEntry item : request.items()) {
            Category c = byId.get(item.id());
            c.setParent(item.parentId() == null ? null : byId.get(item.parentId()));
            c.setSortOrder(item.sortOrder());
        }
        categories.saveAll(byId.values());
        invalidateCache();
    }

    // ── Построение дерева (JdbcTemplate, без N+1) ────────────────────────────────

    private List<CategoryResponse> buildTree(String locale) {
        List<Node> nodes = jdbc.query(
                "select id, parent_id, slug, sort_order, is_active, image_url from categories",
                (rs, i) -> new Node(rs.getLong("id"),
                        (Long) rs.getObject("parent_id"),
                        rs.getString("slug"),
                        rs.getInt("sort_order"),
                        rs.getBoolean("is_active"),
                        rs.getString("image_url")));

        Map<Long, Map<String, String[]>> tr = new HashMap<>();
        jdbc.query("select category_id, locale, name, description from category_translations",
                (RowCallbackHandler) rs -> tr.computeIfAbsent(rs.getLong("category_id"), k -> new HashMap<>())
                        .put(rs.getString("locale"), new String[]{rs.getString("name"), rs.getString("description")}));

        Map<Long, Long> ownCounts = new HashMap<>();
        jdbc.query("select category_id, count(*) as cnt from products where is_active group by category_id",
                (RowCallbackHandler) rs -> ownCounts.put(rs.getLong("category_id"), rs.getLong("cnt")));

        Map<Long, Node> active = new LinkedHashMap<>();
        for (Node n : nodes) {
            if (n.active) {
                n.ownCount = ownCounts.getOrDefault(n.id, 0L);
                active.put(n.id, n);
            }
        }
        List<Node> roots = new ArrayList<>();
        for (Node n : active.values()) {
            Node parent = (n.parentId != null) ? active.get(n.parentId) : null;
            if (parent != null) {
                parent.children.add(n);
            } else {
                roots.add(n); // корень или родитель неактивен/отсутствует
            }
        }
        roots.forEach(this::sortAndCount);
        roots.sort(Comparator.comparingInt(n -> n.sortOrder));
        List<CategoryResponse> result = new ArrayList<>();
        for (Node n : roots) {
            result.add(toResponse(n, locale, tr));
        }
        return result;
    }

    private long sortAndCount(Node node) {
        node.children.sort(Comparator.comparingInt(c -> c.sortOrder));
        long total = node.ownCount;
        for (Node child : node.children) {
            total += sortAndCount(child);
        }
        node.subtreeCount = total;
        return total;
    }

    private CategoryResponse toResponse(Node n, String locale, Map<Long, Map<String, String[]>> tr) {
        Map<String, String[]> byLocale = tr.getOrDefault(n.id, Map.of());
        String[] chosen = byLocale.getOrDefault(locale, byLocale.get(DEFAULT_LOCALE));
        String name = chosen != null ? chosen[0] : n.slug;
        String description = chosen != null ? chosen[1] : null;
        List<CategoryResponse> children = new ArrayList<>();
        for (Node child : n.children) {
            children.add(toResponse(child, locale, tr));
        }
        return new CategoryResponse(n.id, n.slug, n.parentId, n.sortOrder, n.active, n.imageUrl,
                name, description, n.subtreeCount, children);
    }

    private static final class Node {
        final long id;
        final Long parentId;
        final String slug;
        final int sortOrder;
        final boolean active;
        final String imageUrl;
        long ownCount;
        long subtreeCount;
        final List<Node> children = new ArrayList<>();

        Node(long id, Long parentId, String slug, int sortOrder, boolean active, String imageUrl) {
            this.id = id;
            this.parentId = parentId;
            this.slug = slug;
            this.sortOrder = sortOrder;
            this.active = active;
            this.imageUrl = imageUrl;
        }
    }

    // ── Кэш ──────────────────────────────────────────────────────────────────────

    private List<CategoryResponse> cachedTree(String locale) {
        String key = CACHE_PREFIX + locale;
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            try {
                return json.readValue(cached, new TypeReference<List<CategoryResponse>>() {
                });
            } catch (Exception ignored) {
                // повреждённый кэш — перестроим
            }
        }
        List<CategoryResponse> tree = buildTree(locale);
        try {
            redis.opsForValue().set(key, json.writeValueAsString(tree), CACHE_TTL);
        } catch (Exception ignored) {
            // сбой сериализации кэша не должен ломать чтение
        }
        return tree;
    }

    private void invalidateCache() {
        LOCALES.forEach(loc -> redis.delete(CACHE_PREFIX + loc));
    }

    // ── Дерево: обход/поиск ──────────────────────────────────────────────────────

    private List<CategoryResponse> flatten(List<CategoryResponse> nodes, List<CategoryResponse> out) {
        for (CategoryResponse n : nodes) {
            out.add(new CategoryResponse(n.id(), n.slug(), n.parentId(), n.sortOrder(), n.active(),
                    n.imageUrl(), n.name(), n.description(), n.productCount(), null));
            flatten(n.children(), out);
        }
        return out;
    }

    private Optional<CategoryResponse> findBySlug(List<CategoryResponse> nodes, String slug) {
        for (CategoryResponse n : nodes) {
            if (n.slug().equals(slug)) {
                return Optional.of(n);
            }
            Optional<CategoryResponse> found = findBySlug(n.children(), slug);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    // ── Родитель / глубина / цикл ────────────────────────────────────────────────

    private Category resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return categories.findById(parentId)
                .orElseThrow(() -> new ValidationException("catalog.category.parent_not_found"));
    }

    private int depthOf(Category category) {
        int depth = 0;
        Category current = category;
        while (current != null && depth <= MAX_DEPTH + 2) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    private void checkNoCycle(Category category, Category newParent) {
        if (newParent == null) {
            return;
        }
        if (newParent.getId().equals(category.getId()) || descendantIds(category.getId()).contains(newParent.getId())) {
            throw new ConflictException("catalog.category.cycle");
        }
    }

    private void checkDepthForMove(Category category, Category newParent) {
        int newDepth = depthOf(newParent) + 1;
        if (newDepth + subtreeHeight(category.getId()) - 1 > MAX_DEPTH) {
            throw new ConflictException("catalog.category.max_depth");
        }
    }

    private Set<Long> descendantIds(long rootId) {
        Set<Long> result = new HashSet<>();
        List<Long> frontier = new ArrayList<>(List.of(rootId));
        while (!frontier.isEmpty()) {
            List<Long> next = new ArrayList<>();
            for (Long id : frontier) {
                for (Category child : categories.findByParent_Id(id)) {
                    if (result.add(child.getId())) {
                        next.add(child.getId());
                    }
                }
            }
            frontier = next;
        }
        return result;
    }

    private int subtreeHeight(long rootId) {
        int height = 1;
        List<Long> frontier = new ArrayList<>(List.of(rootId));
        Set<Long> seen = new HashSet<>();
        while (!frontier.isEmpty()) {
            List<Long> next = new ArrayList<>();
            for (Long id : frontier) {
                for (Category child : categories.findByParent_Id(id)) {
                    if (seen.add(child.getId())) {
                        next.add(child.getId());
                    }
                }
            }
            if (!next.isEmpty()) {
                height++;
            }
            frontier = next;
        }
        return height;
    }

    private void validateForest(Map<Long, Long> parentOf) {
        for (Long start : parentOf.keySet()) {
            Set<Long> visited = new HashSet<>();
            Long current = start;
            int depth = 0;
            while (current != null) {
                if (!visited.add(current)) {
                    throw new ConflictException("catalog.category.cycle");
                }
                depth++;
                if (depth > MAX_DEPTH) {
                    throw new ConflictException("catalog.category.max_depth");
                }
                current = parentOf.get(current);
            }
        }
    }

    // ── Slug ─────────────────────────────────────────────────────────────────────

    private String resolveSlug(String explicit, CategoryTranslationsRequest tr, Long excludeId) {
        String source = (explicit != null && !explicit.isBlank())
                ? explicit
                : (tr.en() != null ? tr.en().name() : tr.he().name());
        String base = slugify(source);
        if (base.isBlank()) {
            base = "category";
        }
        String candidate = base;
        int suffix = 1;
        while (slugTaken(candidate, excludeId)) {
            suffix++;
            candidate = base + "-" + suffix;
        }
        return candidate;
    }

    private boolean slugTaken(String slug, Long excludeId) {
        return categories.findBySlug(slug)
                .map(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .orElse(false);
    }

    private static String slugify(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    // ── Переводы / вспомогательное ───────────────────────────────────────────────

    private void saveTranslations(Category category, CategoryTranslationsRequest tr) {
        Map<String, CategoryTranslationInput> byLocale = new LinkedHashMap<>();
        byLocale.put("he", tr.he());
        if (tr.ru() != null) {
            byLocale.put("ru", tr.ru());
        }
        if (tr.en() != null) {
            byLocale.put("en", tr.en());
        }
        byLocale.forEach((locale, input) -> {
            CategoryTranslation t = new CategoryTranslation();
            t.setCategory(category);
            t.setLocale(locale);
            t.setName(input.name());
            t.setDescription(input.description());
            t.setMetaTitle(input.metaTitle());
            t.setMetaDescription(input.metaDescription());
            translations.save(t);
        });
    }

    /** Ответ на админскую операцию: локализован в HE; productCount не считается (см. публичные GET). */
    private CategoryResponse adminView(Category category, CategoryTranslationsRequest tr) {
        Long parentId = category.getParent() != null ? category.getParent().getId() : null;
        return new CategoryResponse(category.getId(), category.getSlug(), parentId,
                category.getSortOrder(), category.isActive(), category.getImageUrl(),
                tr.he().name(), tr.he().description(), 0L, null);
    }

    private String resolveLocale(String localeParam) {
        if (localeParam != null) {
            String lower = localeParam.toLowerCase(Locale.ROOT);
            if (LOCALES.contains(lower)) {
                return lower;
            }
        }
        return DEFAULT_LOCALE;
    }
}
