/**
 * Структурированные данные schema.org. Экранируем «<», чтобы название товара или проекта из базы
 * вида «</script>» не закрыло тег и не стало вставкой разметки.
 */
export function JsonLd({ data }: { data: unknown }) {
  const json = JSON.stringify(data).replace(/</g, "\\u003c");
  return <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: json }} />;
}
