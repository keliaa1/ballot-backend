package com.ballot.entity;

/**
 * How new members can join an Organization.
 */
public enum JoinType {
    /** Anyone can join without approval. */
    PUBLIC,
    /** Members request to join and an admin must approve. */
    APPROVAL_REQUIRED,
    /** Only users with the invite code can request to join. */
    INVITE_ONLY
}
