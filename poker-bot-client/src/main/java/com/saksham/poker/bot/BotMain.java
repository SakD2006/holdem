package com.saksham.poker.bot;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point of the headless bots. The bots arrive in Phase 5; for now it only echoes its arguments. */
public final class BotMain {

    private static final Logger log = LoggerFactory.getLogger(BotMain.class);

    private BotMain() {
    }

    public static void main(String[] args) {
        log.info("Bot client started with arguments: {}", Arrays.toString(args));
    }
}
