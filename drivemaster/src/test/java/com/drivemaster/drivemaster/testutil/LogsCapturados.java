package com.drivemaster.drivemaster.testutil;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Captura TODO lo que se escribe en el log (cualquier clase, cualquier nivel, con sus excepciones) mientras corre una acción. */
public final class LogsCapturados {

    private LogsCapturados() {
    }

    public static List<String> de(Runnable accion) {
        Logger raiz = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        Level anterior = raiz.getLevel();
        raiz.setLevel(Level.ALL);
        ListAppender<ILoggingEvent> app = new ListAppender<>();
        app.start();
        raiz.addAppender(app);
        try {
            accion.run();
        } finally {
            raiz.detachAppender(app);
            raiz.setLevel(anterior);
        }
        List<String> lineas = new ArrayList<>();
        for (ILoggingEvent e : app.list) {
            lineas.add(e.getFormattedMessage());
            for (IThrowableProxy t = e.getThrowableProxy(); t != null; t = t.getCause()) {
                lineas.add(t.getClassName() + ": " + t.getMessage());
            }
        }
        return lineas;
    }
}
