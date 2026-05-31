package com.ballot.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A single selectable option within a Ballot.
 * Business rule: minimum 2, maximum 10 options per ballot (enforced in service layer).
 */
@Entity
@Table(name = "ballot_options")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BallotOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ballot_id", nullable = false)
    private Ballot ballot;

    @Column(name = "option_text", nullable = false, length = 500)
    private String optionText;

    @Column(name = "display_order")
    private int displayOrder;
}
