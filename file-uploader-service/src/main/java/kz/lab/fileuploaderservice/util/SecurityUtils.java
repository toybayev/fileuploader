package kz.lab.fileuploaderservice.util;

import lombok.experimental.UtilityClass;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

import java.util.Map;

@UtilityClass
public class SecurityUtils {

    private static final Map<String, Long> USER_ID_MAPPING = Map.of(
            "user1", 1L,
            "user2", 2L,
            "admin", 3L
    );

    public static Mono<Long> getCurrentUserId(){
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .map(auth -> auth.getName())
                .flatMap(username -> {
                    Long userId = USER_ID_MAPPING.get(username);

                    if (userId == null){
                        return Mono.error(new IllegalStateException(
                                "User ID not found for username: " + username
                        ));
                    }

                    return Mono.just(userId);
                });
    }

    public static Mono<String> getCurrentUsername(){
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .map(auth -> auth.getName());
    }


    public static Mono<Boolean> isAdmin(){
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .map(auth -> auth.getAuthorities().stream()
                        .anyMatch(grantedAuthority ->
                                grantedAuthority.getAuthority().equals("ROLE_ADMIN")
                        )
                );
    }

}
