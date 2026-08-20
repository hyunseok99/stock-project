package org.team4.sol_server.domain.fail;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "failed_kafka_events")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    private String topic;

    @Column(columnDefinition = "TEXT")
    private String payload; // json 원본

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private LocalDateTime createdAt;

}
