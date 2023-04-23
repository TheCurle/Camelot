package uk.gemwire.camelot;

import org.jdbi.v3.core.Jdbi;
import uk.gemwire.camelot.db.schemas.ModLogEntry;
import uk.gemwire.camelot.db.transactionals.ModLogsDAO;

import java.time.Duration;

public class Hello {
    public static void main(String[] args) {
        final Jdbi jdbi = BotMain.createDatabaseConnection();
        jdbi.useExtension(ModLogsDAO.class, handle -> {
            for (int i = 1; i < 100; i++) {
                handle.insert(ModLogEntry.warn(
                        561254664750891009L, 853270691176906802L, 561254664750891009L, "Being an idiot nr." + i
                ));
            }
        });
    }
}
