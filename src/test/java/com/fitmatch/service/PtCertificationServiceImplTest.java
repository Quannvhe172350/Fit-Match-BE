package com.fitmatch.service;

import com.fitmatch.dto.pt.CertificationRequest;
import com.fitmatch.dto.pt.CertificationResponse;
import com.fitmatch.entity.PtCertification;
import com.fitmatch.entity.PtProfile;
import com.fitmatch.exception.ResourceNotFoundException;
import com.fitmatch.repository.PtCertificationRepository;
import com.fitmatch.repository.PtProfileRepository;
import com.fitmatch.service.impl.PtCertificationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PtCertificationServiceImplTest {

    @Mock private PtCertificationRepository certificationRepository;
    @Mock private PtProfileRepository ptProfileRepository;
    @InjectMocks private PtCertificationServiceImpl service;

    @Test
    void add_persistsForOwner() {
        PtProfile profile = PtProfile.builder().id(1L).build();
        when(ptProfileRepository.findByUser_Username("john")).thenReturn(Optional.of(profile));
        when(certificationRepository.save(any(PtCertification.class))).thenAnswer(inv -> {
            PtCertification c = inv.getArgument(0);
            c.setId(7L);
            return c;
        });

        CertificationResponse res = service.add("john",
                CertificationRequest.builder().name("NASM").build());

        assertThat(res.getId()).isEqualTo(7L);
        assertThat(res.getName()).isEqualTo("NASM");
    }

    @Test
    void delete_notOwned_throwsNotFound() {
        when(certificationRepository.findByIdAndPtProfile_User_Username(99L, "john"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("john", 99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(certificationRepository, never()).delete(any());
    }
}
