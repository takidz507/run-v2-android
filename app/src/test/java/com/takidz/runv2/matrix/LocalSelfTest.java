package com.takidz.runv2.matrix;

import com.takidz.runv2.model.Friend;
import com.takidz.runv2.model.Result;
import com.takidz.runv2.model.Room;
import com.takidz.runv2.model.TurnCredentials;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Offline self-test for the Matrix client layer. Pure Java, no network, no
 * account, no Android runtime. Run with:
 * <pre>
 *   javac -cp okhttp-4.12.0.jar:okio-3.6.0.jar:kotlin-stdlib-1.9.10.jar:annotations-13.0.jar:gson-2.10.1.jar \
 *     -d /tmp/matrix-classes $(find app/src/main/java/com/takidz/runv2/model app/src/main/java/com/takidz/runv2/matrix -name '*.java' ! -name 'AndroidSecureStore.java') \
 *     app/src/test/java/com/takidz/runv2/matrix/LocalSelfTest.java
 *   java -cp /tmp/matrix-classes:... LocalSelfTest
 * </pre>
 * (AndroidSecureStore is excluded: it needs the Android SDK + security-crypto
 * jar. It is a 4-method delegate; verify it via the Gradle build instead.)
 *
 * Exit code 0 = all checks passed; 1 = at least one failed.
 */
public final class LocalSelfTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        check("config base URL is the Matrix API host",
                "https://matrix.tchncs.de".equals(MatrixConfig.BASE_URL));
        check("config API path", "/_matrix/client/v3".equals(MatrixConfig.CLIENT_API_PATH));

        testResult();
        testPinBackup();
        testInviteLinks();
        testFriendStore();
        testModels();
        testClientOfflineAndValidation();

        System.out.println("LocalSelfTest: " + passed + " passed, " + failed + " failed.");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testResult() {
        Result<String> ok = Result.ok("v");
        check("Result.ok is success", ok.isSuccess() && "v".equals(ok.getValue()));
        Result<String> err = Result.error("OFFLINE", "No connection.");
        check("Result.error is error", err.isError()
                && "OFFLINE".equals(err.getErrorCode())
                && "No connection.".equals(err.getErrorMessage()));
    }

    private static void testPinBackup() {
        PinBackup pinBackup = new PinBackup();
        char[] pin = "123456".toCharArray();
        Result<PinBackup.EncryptedBlob> enc = pinBackup.encryptText("backup-payload", pin);
        check("PinBackup encrypt succeeds", enc.isSuccess());
        check("PinBackup encrypt wipes the PIN array", allZero(pin));
        check("PinBackup blob version is 1", enc.isSuccess() && enc.getValue().getVersion() == 1);

        Result<String> roundTrip = pinBackup.decryptText(enc.getValue(), "123456".toCharArray());
        check("PinBackup decrypt round-trips", roundTrip.isSuccess()
                && "backup-payload".equals(roundTrip.getValue()));

        Result<PinBackup.EncryptedBlob> fromJson =
                PinBackup.EncryptedBlob.fromJson(enc.getValue().toJson());
        check("PinBackup blob JSON round-trips", fromJson.isSuccess());
        Result<String> viaJson = pinBackup.decryptText(fromJson.getValue(), "123456".toCharArray());
        check("PinBackup decrypts from JSON blob", viaJson.isSuccess()
                && "backup-payload".equals(viaJson.getValue()));

        Result<String> wrongPin = pinBackup.decryptText(enc.getValue(), "654321".toCharArray());
        check("PinBackup wrong PIN fails CRYPTO_ERROR", wrongPin.isError()
                && "CRYPTO_ERROR".equals(wrongPin.getErrorCode()));

        Result<PinBackup.EncryptedBlob> shortPin =
                pinBackup.encryptText("x", "12345".toCharArray());
        check("PinBackup 5-digit PIN rejected", shortPin.isError()
                && "INVALID_ARGUMENT".equals(shortPin.getErrorCode()));
        Result<PinBackup.EncryptedBlob> alphaPin =
                pinBackup.encryptText("x", "12ab56".toCharArray());
        check("PinBackup non-digit PIN rejected", alphaPin.isError()
                && "INVALID_ARGUMENT".equals(alphaPin.getErrorCode()));

        Result<PinBackup.EncryptedBlob> badBlob = PinBackup.EncryptedBlob.fromJson("{not json");
        check("PinBackup malformed blob rejected", badBlob.isError());

        Result<byte[]> empty = pinBackup.decrypt(null, "123456".toCharArray());
        check("PinBackup null blob rejected", empty.isError());
    }

    private static void testInviteLinks() {
        String roomLink = InviteLinks.buildRoomLink("RUNAB12CD");
        check("InviteLinks room link format", "run://join/RUNAB12CD".equals(roomLink));
        Result<InviteLinks.ParsedInvite> parsed = InviteLinks.parse(roomLink);
        check("InviteLinks room link parses", parsed.isSuccess()
                && parsed.getValue().getKind() == InviteLinks.Kind.ROOM
                && "RUNAB12CD".equals(parsed.getValue().getRoomCode()));

        String friendLink = InviteLinks.buildFriendLink("@taki:tchncs.de");
        check("InviteLinks friend link encodes user ID",
                "run://friend/%40taki%3Atchncs.de".equals(friendLink));
        Result<InviteLinks.ParsedInvite> parsedFriend = InviteLinks.parse(friendLink);
        check("InviteLinks friend link parses", parsedFriend.isSuccess()
                && parsedFriend.getValue().getKind() == InviteLinks.Kind.FRIEND
                && "@taki:tchncs.de".equals(parsedFriend.getValue().getUserId()));

        check("InviteLinks rejects http link", InviteLinks.parse("https://example.org/x").isError());
        check("InviteLinks rejects unknown host", InviteLinks.parse("run://bogus/ABC").isError());
        check("InviteLinks rejects empty code", InviteLinks.parse("run://join/").isError());

        String code = InviteLinks.generateRoomCode();
        check("InviteLinks generated code is 8 upper-case alphanumerics",
                code != null && code.matches("[A-Z0-9]{8}"));

        InMemorySecureStore store = new InMemorySecureStore();
        InviteLinks links = new InviteLinks(store);
        check("InviteLinks unknown code is NOT_FOUND",
                links.resolveRoomCode("RUNAB12CD").isError()
                        && "NOT_FOUND".equals(links.resolveRoomCode("RUNAB12CD").getErrorCode()));
        check("InviteLinks register rejects bad room ID",
                links.registerRoomCode("RUNAB12CD", "not-a-room").isError());
        check("InviteLinks register ok",
                links.registerRoomCode("RUNAB12CD", "!abc123:tchncs.de").isSuccess());
        check("InviteLinks resolve ok",
                links.resolveRoomCode("RUNAB12CD").isSuccess()
                        && "!abc123:tchncs.de".equals(links.resolveRoomCode("RUNAB12CD").getValue()));
        InviteLinks reloaded = new InviteLinks(store);
        check("InviteLinks mapping survives reload",
                reloaded.resolveRoomCode("RUNAB12CD").isSuccess()
                        && "!abc123:tchncs.de".equals(reloaded.resolveRoomCode("RUNAB12CD").getValue()));
    }

    private static void testFriendStore() {
        InMemorySecureStore store = new InMemorySecureStore();
        FriendStore friends = new FriendStore(store);
        check("FriendStore starts empty", friends.size() == 0);

        Friend taki = new Friend("@taki:tchncs.de", "Taki", "mxc://tchncs.de/aaa",
                Friend.Presence.ONLINE, 0L, null);
        check("FriendStore add ok", friends.addFriend(taki).isSuccess());
        check("FriendStore get by user ID", friends.getFriend("@taki:tchncs.de") == taki);
        check("FriendStore rejects blank user ID",
                friends.addFriend(new Friend("", "No", null)).isError());
        friends.addFriend(new Friend("@amine:tchncs.de", "Amine", null));
        List<Friend> all = friends.getAllFriends();
        check("FriendStore lists sorted by display name", all.size() == 2
                && "Amine".equals(all.get(0).getDisplayName()));

        FriendStore reloaded = new FriendStore(store);
        check("FriendStore persists across instances", reloaded.size() == 2
                && reloaded.getFriend("@taki:tchncs.de") != null
                && "Taki".equals(reloaded.getFriend("@taki:tchncs.de").getDisplayName())
                && "mxc://tchncs.de/aaa".equals(reloaded.getFriend("@taki:tchncs.de").getAvatarUrl()));

        check("FriendStore remove unknown is NOT_FOUND",
                friends.removeFriend("@ghost:tchncs.de").isError());
        check("FriendStore remove ok", friends.removeFriend("@amine:tchncs.de").isSuccess());
        check("FriendStore size after remove", friends.size() == 1);

        store.putString(FriendStore.STORE_KEY, "{corrupt json");
        FriendStore corrupt = new FriendStore(store);
        check("FriendStore tolerates corrupt stored JSON", corrupt.size() == 0);

        MatrixHttpClient client = new MatrixHttpClient();
        check("FriendStore refreshPresence without login is NOT_LOGGED_IN",
                friends.refreshPresence(client).isError()
                        && "NOT_LOGGED_IN".equals(friends.refreshPresence(client).getErrorCode()));
        client.close();
    }

    private static void testModels() {
        Room room = new Room("!abc:tchncs.de", null, "#general:tchncs.de", "Topic",
                "mxc://tchncs.de/xyz", 5, "hello", 123L);
        check("Room display name falls back to alias", "#general:tchncs.de".equals(room.getDisplayName()));
        check("Room getRoomId aliases getId", "!abc:tchncs.de".equals(room.getRoomId()));
        check("Room fields round-trip", room.getMemberCount() == 5
                && "hello".equals(room.getLastMessage()) && room.getLastTimestamp() == 123L);

        Room legacy = new Room("room-1", "Section D", "hi", 42, "RUN-DSEC", true);
        check("Room legacy constructor still works", "room-1".equals(legacy.getId())
                && "run://join/RUN-DSEC".equals(legacy.getInviteLink()));
        Room noCode = new Room("!x:tchncs.de", "X", null, null, null, 2, null, 0L);
        check("Room without invite code has null invite link", noCode.getInviteLink() == null);

        Friend legacyFriend = new Friend("@taki", "Taki", true);
        check("Friend legacy constructor still works", "@taki".equals(legacyFriend.getUsername())
                && legacyFriend.isOnline() && "@taki".equals(legacyFriend.getUserId()));
        Friend updated = legacyFriend.withPresence(Friend.Presence.OFFLINE, 60000L, "away");
        check("Friend withPresence updates presence", updated.getPresence() == Friend.Presence.OFFLINE
                && !updated.isOnline() && updated.getLastActiveAgoMs() == 60000L);

        TurnCredentials turn = new TurnCredentials("user", "secret-turn-pw",
                Arrays.asList("turn:example.org:3478"), 3600);
        check("TurnCredentials toString never contains the password",
                !turn.toString().contains("secret-turn-pw"));
        check("TurnCredentials fields", "user".equals(turn.getUsername()) && turn.getTtlSeconds() == 3600);
    }

    private static void testClientOfflineAndValidation() {
        MatrixHttpClient client = new MatrixHttpClient();
        check("Client starts logged out", !client.isLoggedIn());

        client.setOffline(true);
        check("Offline login fails OFFLINE",
                client.login("taki", "secret".toCharArray()).isError()
                        && "OFFLINE".equals(client.login("taki", "x".toCharArray()).getErrorCode()));
        check("Offline sync fails OFFLINE", client.sync().isError()
                && "OFFLINE".equals(client.sync().getErrorCode()));
        check("Offline joined rooms fails OFFLINE", client.getJoinedRooms().isError());
        check("Offline TURN fails OFFLINE", client.getTurnServer().isError());
        client.setOffline(false);

        char[] pw = "secret".toCharArray();
        Result<MatrixHttpClient.LoginResult> blank = client.login("   ", pw);
        check("Blank username rejected", blank.isError()
                && "INVALID_ARGUMENT".equals(blank.getErrorCode()));
        check("Login wipes the password array", allZero(pw));

        check("Sync without login is NOT_LOGGED_IN", client.sync().isError()
                && "NOT_LOGGED_IN".equals(client.sync().getErrorCode()));
        check("Send without login is NOT_LOGGED_IN", client.sendMessage("!x:tchncs.de", "hi").isError());
        check("Join blank room rejected", client.joinRoom("  ").isError());
        check("Search users blank rejected", client.searchUsers("  ", 10).isError());

        check("normalizeUsername strips @ and server",
                "taki".equals(MatrixHttpClient.normalizeUsername("@taki:tchncs.de")));
        check("normalizeUsername keeps bare name",
                "taki".equals(MatrixHttpClient.normalizeUsername("taki")));
        check("normalizeUserId expands bare name",
                "@taki:tchncs.de".equals(MatrixHttpClient.normalizeUserId("taki")));
        check("normalizeUserId keeps full ID",
                "@taki:tchncs.de".equals(MatrixHttpClient.normalizeUserId("@taki:tchncs.de")));

        check("restoreSession rejects blank token",
                client.restoreSession("@taki:tchncs.de", "  ").isError());
        check("restoreSession accepts a session",
                client.restoreSession("@taki:tchncs.de", "token123").isSuccess()
                        && client.isLoggedIn()
                        && "@taki:tchncs.de".equals(client.getCurrentUsername()));
        client.clearLocalSession();
        check("clearLocalSession logs out", !client.isLoggedIn());
        client.close();
    }

    private static boolean allZero(char[] chars) {
        for (char c : chars) {
            if (c != '\0') {
                return false;
            }
        }
        return true;
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS: " + name);
        } else {
            failed++;
            System.out.println("  FAIL: " + name);
        }
    }
}
