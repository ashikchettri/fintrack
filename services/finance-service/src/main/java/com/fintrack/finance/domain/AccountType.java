package com.fintrack.finance.domain;

/** Kinds of account. Mirrors the CHECK constraint in V1. */
public enum AccountType {
    CHECKING,
    SAVINGS,
    CREDIT_CARD,
    CASH,
    INVESTMENT,
    OTHER
}
