-- Household invitations (multi-member households). An OWNER invites someone by
-- email; the invitee accepts with a one-time code and joins the EXISTING
-- household as a member — instead of signup creating a fresh single-member one.
-- Same one-time-code hardening as verification/reset (ADR 004/005): code hashed
-- at rest, TTL, attempt cap.
--
-- display_name gives members a human label for the shared-commitments view
-- (ADR 006) — otherwise the household screen can only show "Housemate".

ALTER TABLE household_members ADD COLUMN display_name VARCHAR(100);

CREATE TABLE household_invites (
    id            UUID PRIMARY KEY,
    household_id  UUID         NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    invited_by    UUID         NOT NULL REFERENCES household_members (id) ON DELETE CASCADE,
    email         VARCHAR(320) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    code_hash     VARCHAR(64)  NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    attempts      INT          NOT NULL DEFAULT 0,
    accepted_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_household_invites_role CHECK (role IN ('OWNER', 'ADULT', 'CHILD'))
);

-- accept looks up by invitee email; the owner lists a household's invites
CREATE INDEX idx_household_invites_email ON household_invites (email);
CREATE INDEX idx_household_invites_household ON household_invites (household_id);
