package com.tzevabest.content.service;

import com.tzevabest.common.web.error.ValidationException;
import com.tzevabest.common.web.security.CurrentUser;
import com.tzevabest.content.domain.SiteImage;
import com.tzevabest.content.dto.SiteImageView;
import com.tzevabest.content.repository.SiteImageRepository;
import com.tzevabest.media.service.MediaStorage;
import com.tzevabest.media.service.StoredObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SiteImageServiceTest {

    @Mock
    private SiteImageRepository repo;
    @Mock
    private MediaStorage storage;
    @Mock
    private CurrentUser currentUser;

    @InjectMocks
    private SiteImageService service;

    /** Валидный PNG: сигнатура + добивка до >=12 байт (требование ContentTypeSniffer). */
    private static MultipartFile png() {
        byte[] data = new byte[16];
        byte[] sig = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(sig, 0, data, 0, sig.length);
        return new MockMultipartFile("file", "can.png", "image/png", data);
    }

    @Test
    void upload_unknownSlot_rejected() {
        assertThatThrownBy(() -> service.upload("not_a_slot", png()))
                .isInstanceOf(ValidationException.class);
        verify(storage, never()).put(anyString(), anyString(), anyString(), any());
    }

    @Test
    void upload_nonImageContent_rejected() {
        MultipartFile text = new MockMultipartFile("file", "x.txt", "image/png",
                "just some plain text, not an image".getBytes());
        assertThatThrownBy(() -> service.upload("hero_can", text))
                .isInstanceOf(ValidationException.class);
        verify(storage, never()).put(anyString(), anyString(), anyString(), any());
    }

    @Test
    void upload_validPng_storesUnderSlotPrefixAndReturnsUrl() {
        when(repo.findById("hero_can")).thenReturn(Optional.empty());
        when(currentUser.optional()).thenReturn(Optional.empty());
        when(storage.put(eq("site/hero_can"), anyString(), eq("image/png"), any()))
                .thenReturn(new StoredObject("site/hero_can/abc.png", "https://media.example/site/hero_can/abc.png"));

        SiteImageView view = service.upload("hero_can", png());

        assertThat(view.slot()).isEqualTo("hero_can");
        assertThat(view.url()).isEqualTo("https://media.example/site/hero_can/abc.png");
        assertThat(view.contentType()).isEqualTo("image/png");
        ArgumentCaptor<SiteImage> saved = ArgumentCaptor.forClass(SiteImage.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getObjectKey()).isEqualTo("site/hero_can/abc.png");
        assertThat(saved.getValue().getSlot()).isEqualTo("hero_can");
    }

    @Test
    void upload_replacesOldObject() {
        SiteImage old = new SiteImage();
        old.setSlot("hero_can");
        old.setObjectKey("site/hero_can/old.png");
        when(repo.findById("hero_can")).thenReturn(Optional.of(old));
        when(currentUser.optional()).thenReturn(Optional.empty());
        when(storage.put(anyString(), anyString(), anyString(), any()))
                .thenReturn(new StoredObject("site/hero_can/new.png", "https://media.example/site/hero_can/new.png"));

        service.upload("hero_can", png());

        verify(storage).delete("site/hero_can/old.png");
    }

    @Test
    void reset_existing_deletesObjectAndRow() {
        SiteImage img = new SiteImage();
        img.setSlot("banner");
        img.setObjectKey("site/banner/a.png");
        when(repo.findById("banner")).thenReturn(Optional.of(img));

        service.reset("banner");

        verify(storage).delete("site/banner/a.png");
        verify(repo).delete(img);
    }

    @Test
    void adminList_includesAllKnownSlots_urlNullWhenUnset() {
        when(repo.findAll()).thenReturn(List.of());
        List<SiteImageView> list = service.adminList();
        assertThat(list).extracting(SiteImageView::slot)
                .contains("hero_can", "works_before", "works_after", "banner", "logo", "social_1", "social_6");
        assertThat(list).allSatisfy(v -> assertThat(v.url()).isNull());
    }

    @Test
    void publicMap_returnsOnlySetSlots() {
        SiteImage img = new SiteImage();
        img.setSlot("hero_can");
        img.setObjectKey("site/hero_can/a.png");
        when(repo.findAll()).thenReturn(List.of(img));
        when(storage.publicUrl("site/hero_can/a.png")).thenReturn("https://media.example/site/hero_can/a.png");

        Map<String, String> map = service.publicMap();

        assertThat(map).containsExactly(Map.entry("hero_can", "https://media.example/site/hero_can/a.png"));
    }
}
