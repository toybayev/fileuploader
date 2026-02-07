package kz.lab.fileuploaderservice.saga.repository;

import kz.lab.fileuploaderservice.saga.model.SagaStateEntity;
import kz.lab.fileuploaderservice.saga.model.SagaStatus;
import kz.lab.fileuploaderservice.saga.model.SagaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.util.UUID;

@SpringBootTest
class SagaStateRepositoryTest {


    @Autowired
    private SagaStateRepository sagaStateRepository;

    @Test
    void shouldSaveAndFindSaga() {
        // Создаём Saga
        SagaStateEntity saga = new SagaStateEntity(
                UUID.randomUUID(),
                SagaType.FILE_UPLOAD,
                1L,
                UUID.randomUUID()
        );

        // Сохраняем и проверяем
        StepVerifier.create(
                        sagaStateRepository.save(saga)
                                .flatMap(saved -> sagaStateRepository.findBySagaId(saved.getSagaId()))
                )
                .expectNextMatches(found ->
                        found.getSagaType() == SagaType.FILE_UPLOAD &&
                        found.getStatus() == SagaStatus.IN_PROGRESS
                )
                .verifyComplete();
    }

}
