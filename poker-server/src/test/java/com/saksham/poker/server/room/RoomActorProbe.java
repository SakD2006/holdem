package com.saksham.poker.server.room;

/** Waits for a room on its own thread to finish what it is doing. */
final class RoomActorProbe {

    private RoomActorProbe() {
    }

    /** Returns once the room has stopped finishing hands for a moment, which a paused room soon does. */
    static void awaitQuiet(RoomManager manager, String code, AutoPlayer witness) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        int seen = -1;
        while (System.currentTimeMillis() < deadline) {
            int now = witness.handsEnded.get();
            if (now == seen && manager.find(code).isPresent()) {
                return;
            }
            seen = now;
            Thread.sleep(200);
        }
        throw new AssertionError("Room " + code + " did not settle");
    }
}
