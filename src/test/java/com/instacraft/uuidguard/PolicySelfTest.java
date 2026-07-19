package com.instacraft.uuidguard;

import java.util.UUID;

public final class PolicySelfTest {
    private int assertions;

    public static void main(String[] args) {
        new PolicySelfTest().run();
    }

    private void run() {
        GuardSettings settings = GuardSettings.defaults();
        IdentityPolicy policy = new IdentityPolicy(settings);

        UUID bedrockUuid = UUID.fromString("00000000-0000-0000-0009-01fb2cefeb69");
        UUID otherBedrockUuid = UUID.fromString("00000000-0000-0000-0009-01fb2cefeb70");
        UUID javaUuid = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        UUID otherJavaUuid = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");

        LoginIdentity returningBedrock = bedrock("GAMERBOY3216805", bedrockUuid);
        StoredIdentity storedBedrock = stored("GAMERBOY3216805", bedrockUuid, IdentityKind.BEDROCK);
        assertDecision(
                policy.evaluate(returningBedrock, storedBedrock, true),
                DecisionType.ALLOW_MATCH,
                true,
                "returning Bedrock player with offline presented UUID is allowed by canonical Floodgate UUID"
        );

        StoredIdentity storedOfflineBug = stored(
                "GAMERBOY3216805",
                UuidUtil.offlineUuid("GAMERBOY3216805"),
                IdentityKind.UNKNOWN
        );
        assertDecision(
                policy.evaluate(returningBedrock, storedOfflineBug, true),
                DecisionType.ALLOW_MIGRATE_BEDROCK_OFFLINE_UUID,
                true,
                "Bedrock record polluted by offline UUID migrates to Floodgate UUID"
        );

        StoredIdentity storedJava = stored("GAMERBOY3216805", javaUuid, IdentityKind.JAVA);
        assertDecision(
                policy.evaluate(returningBedrock, storedJava, true),
                DecisionType.BLOCK_CROSS_PLATFORM_NAME,
                false,
                "Bedrock cannot take an existing Java visible name"
        );

        LoginIdentity javaSameName = LoginIdentity.javaPlayer("GAMERBOY3216805", javaUuid);
        assertDecision(
                policy.evaluate(javaSameName, storedBedrock, true),
                DecisionType.BLOCK_CROSS_PLATFORM_NAME,
                false,
                "Java cannot take an existing Bedrock visible name"
        );

        LoginIdentity newJava = LoginIdentity.javaPlayer("NewJavaName", javaUuid);
        assertDecision(
                policy.evaluate(newJava, null, true),
                DecisionType.ALLOW_REGISTER,
                true,
                "new Java name registers"
        );

        StoredIdentity sameJava = stored("NewJavaName", javaUuid, IdentityKind.JAVA);
        assertDecision(
                policy.evaluate(newJava, sameJava, true),
                DecisionType.ALLOW_MATCH,
                true,
                "same Java UUID can return"
        );

        StoredIdentity differentJava = stored("NewJavaName", otherJavaUuid, IdentityKind.JAVA);
        assertDecision(
                policy.evaluate(newJava, differentJava, true),
                DecisionType.BLOCK_UUID_MISMATCH,
                false,
                "different Java UUID on same name is blocked"
        );

        LoginIdentity otherBedrockSameName = bedrock("GAMERBOY3216805", otherBedrockUuid);
        assertDecision(
                policy.evaluate(otherBedrockSameName, storedBedrock, true),
                DecisionType.BLOCK_UUID_MISMATCH,
                false,
                "different Floodgate UUID on same Bedrock name is blocked"
        );

        GuardSettings failClosed = new GuardSettings(
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                "player_uuid_data",
                "uuid_guard_identity_meta",
                5000,
                5000
        );
        assertDecision(
                new IdentityPolicy(failClosed).evaluate(newJava, null, false),
                DecisionType.BLOCK_DATABASE_UNAVAILABLE,
                false,
                "optional fail-closed mode blocks when cache is unavailable"
        );
        assertWrite(
                new IdentityPolicy(settings).evaluate(newJava, null, false),
                DecisionType.ALLOW_DATABASE_UNAVAILABLE,
                true,
                false,
                "default fail-open mode allows without mutating identity state"
        );

        StoredIdentity unknownFloodgate = stored("FloodName", bedrockUuid, IdentityKind.UNKNOWN);
        LoginIdentity javaFloodName = LoginIdentity.javaPlayer("FloodName", javaUuid);
        assertDecision(
                policy.evaluate(javaFloodName, unknownFloodgate, true),
                DecisionType.BLOCK_CROSS_PLATFORM_NAME,
                false,
                "legacy Floodgate UUID records are inferred as Bedrock"
        );

        LoginIdentity linkedBedrock = new LoginIdentity(
                "LinkedName",
                LoginIdentity.key("LinkedName"),
                javaUuid,
                javaUuid,
                IdentityKind.LINKED_BEDROCK,
                bedrockUuid,
                "901fb2cefeb69",
                "test"
        );
        StoredIdentity linkedStored = stored("LinkedName", javaUuid, IdentityKind.JAVA);
        assertDecision(
                policy.evaluate(linkedBedrock, linkedStored, true),
                DecisionType.ALLOW_MATCH,
                true,
                "linked Bedrock accounts use their linked Java identity"
        );

        System.out.println("PolicySelfTest passed " + assertions + " assertions.");
    }

    private LoginIdentity bedrock(String name, UUID floodgateUuid) {
        return new LoginIdentity(
                name,
                LoginIdentity.key(name),
                UuidUtil.offlineUuid(name),
                floodgateUuid,
                IdentityKind.BEDROCK,
                floodgateUuid,
                "901fb2cefeb69",
                "test"
        );
    }

    private StoredIdentity stored(String name, UUID uuid, IdentityKind kind) {
        return new StoredIdentity(
                LoginIdentity.key(name),
                name,
                uuid,
                kind,
                UuidUtil.isFloodgateId(uuid) ? uuid : null,
                null,
                null
        );
    }

    private void assertDecision(LoginDecision actual, DecisionType type, boolean allowed, String message) {
        assertions++;
        if (actual.type() != type || actual.allowed() != allowed) {
            throw new AssertionError(message + ": expected " + type + "/" + allowed
                    + " but got " + actual.type() + "/" + actual.allowed());
        }
    }

    private void assertWrite(
            LoginDecision actual,
            DecisionType type,
            boolean allowed,
            boolean shouldWrite,
            String message
    ) {
        assertions++;
        if (actual.type() != type || actual.allowed() != allowed || actual.shouldWriteCanonical() != shouldWrite) {
            throw new AssertionError(message + ": expected " + type + "/" + allowed + "/" + shouldWrite
                    + " but got " + actual.type() + "/" + actual.allowed() + "/" + actual.shouldWriteCanonical());
        }
    }
}
