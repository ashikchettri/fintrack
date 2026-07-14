package com.fintrack.finance.domain;

/** How a transaction entered the system. */
public enum TransactionSource {
    /** Entered by a member through the API/UI. */
    MANUAL,
    /** Created by a CSV statement import (the hero onboarding flow). */
    CSV_IMPORT
}
