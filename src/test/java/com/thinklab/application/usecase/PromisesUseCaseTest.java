package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.PromiseResponse;
import com.thinklab.application.usecase.CapacityAssessor.Judged;
import com.thinklab.domain.exception.CapacityAccessDeniedException;
import com.thinklab.domain.model.CapacityPolicy;
import com.thinklab.domain.model.CapacityPolicy.Assessment;
import com.thinklab.domain.model.CapacityPolicy.Forecast;
import com.thinklab.domain.model.CapacityPolicy.Level;
import com.thinklab.domain.model.CapacityPolicy.Metric;
import com.thinklab.domain.model.CapacityPolicy.PolicyStatus;
import com.thinklab.domain.repository.CapacityPolicyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromisesUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private final UUID org = UUID.randomUUID();
    private final UUID asset = UUID.randomUUID();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private CapacityPolicy policy(String name, Instant measured) {
        return CapacityPolicy.reconstitute(UUID.randomUUID(), org, name, asset, Metric.DISK, 100, 70, 90, 30, 30, 24, PolicyStatus.ACTIVE, NOW.minus(Duration.ofDays(90)),
                NOW.minus(Duration.ofDays(90)), 50.0, measured, List.of());
    }

    private static Assessment at(Level level) {
        return new Assessment(level, 50.0, false, Forecast.STABLE, null, null);
    }

    private RetrievePromisesUseCase useCase(List<Judged> judged) {
        CapacityPolicyRepository repository = mock(CapacityPolicyRepository.class);
        CapacityAssessor assessor = mock(CapacityAssessor.class);
        when(repository.findAll(org)).thenReturn(Flux.fromIterable(judged).map(Judged::policy));
        when(assessor.judge(any())).thenAnswer(call -> Mono.just(judged.stream().filter(j -> j.policy() == call.getArgument(0)).findFirst().orElseThrow()));
        return new RetrievePromisesUseCase(repository, assessor, clock);
    }

    @Test
    @DisplayName("a policy at the critical level is broken with CAPACITY_CRITICAL; one that changed lately is listed as kept with its latest sample as when it changed; a quiet one is left out")
    void selects() {
        CapacityPolicy critical = policy("Critical", NOW.minus(Duration.ofDays(60)));
        CapacityPolicy recovered = policy("Recovered", NOW.minus(Duration.ofHours(2)));
        CapacityPolicy quiet = policy("Quiet", NOW.minus(Duration.ofDays(60)));

        StepVerifier.create(useCase(List.of(new Judged(quiet, at(Level.OK)), new Judged(recovered, at(Level.OK)), new Judged(critical, at(Level.CRITICAL)))).execute(org, null, null, "ENGINEER"))
                .assertNext(promises -> {
                    assertEquals(List.of("Critical", "Recovered"), promises.stream().map(PromiseResponse::name).toList());
                    assertEquals("BROKEN", promises.get(0).verdict());
                    assertEquals("CAPACITY_CRITICAL", promises.get(0).code());
                    assertEquals(asset, promises.get(0).assetId());
                    assertEquals("ACTIVE", promises.get(0).status());
                    assertEquals("KEPT", promises.get(1).verdict());
                    assertEquals(NOW.minus(Duration.ofHours(2)), promises.get(1).changedAt());
                }).verifyComplete();
    }

    @Test
    @DisplayName("the window and the limit apply to each kind")
    void window() {
        CapacityPolicy keptOld = policy("KeptOld", NOW.minus(Duration.ofHours(30)));
        CapacityPolicy kept = policy("Kept", NOW.minus(Duration.ofHours(5)));
        CapacityPolicy one = policy("B1", NOW.minus(Duration.ofDays(60)));
        CapacityPolicy two = policy("B2", NOW.minus(Duration.ofDays(60)));

        StepVerifier.create(useCase(List.of(new Judged(keptOld, at(Level.OK)), new Judged(kept, at(Level.WARNING)), new Judged(one, at(Level.CRITICAL)), new Judged(two, at(Level.CRITICAL))))
                .execute(org, 1, 1, "ENGINEER")).assertNext(promises -> assertEquals(List.of("B1", "Kept"), promises.stream().map(PromiseResponse::name).toList())).verifyComplete();
    }

    @Test
    @DisplayName("the days must be 1 to 365 and the limit 1 to 1000; a requester is refused")
    void validation() {
        RetrievePromisesUseCase useCase = useCase(List.of());

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(org, 0, null, "ENGINEER").block());
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(org, 366, null, "ENGINEER").block());
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(org, null, 0, "ENGINEER").block());
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(org, null, 1001, "ENGINEER").block());
        assertThrows(CapacityAccessDeniedException.class, () -> useCase.execute(org, null, null, "REQUESTER").block());
    }

    @Test
    @DisplayName("the selection is a utility class")
    void utility() throws ReflectiveOperationException {
        Constructor<PromiseSelection> constructor = PromiseSelection.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }
}
