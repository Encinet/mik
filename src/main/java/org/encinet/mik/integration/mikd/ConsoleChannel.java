package org.encinet.mik.integration.mikd;

import org.encinet.mik.protocol.ConsoleCommandPoll;
import org.encinet.mik.protocol.ConsoleCommandReceipt;
import org.encinet.mik.protocol.ConsoleLogPage;
import org.encinet.mik.protocol.ConsoleLogPoll;
import org.encinet.mik.protocol.ConsoleLogReceipt;
import org.encinet.mik.protocol.ConsoleRegistration;
import org.encinet.mik.protocol.Request;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

record ConsoleChannel(ConsoleRegistration registration, Request initial, Handler handler) {
    private static final int POLL_TIMEOUT_MS = 2000;
    private static final int MAX_WORK_MS = 3000;
    private static final Request NEXT_COMMAND = commandPoll(ConsoleCommandPoll.getDefaultInstance());
    private static final Request NEXT_LOG_QUERY = logPoll(ConsoleLogPoll.getDefaultInstance());

    @FunctionalInterface
    interface Handler {
        Request next(MikdClient.Exchange exchange) throws IOException, InterruptedException;
    }

    static ConsoleChannel commands(ConsoleRegistration registration, ConsolePort.CommandInput input) {
        return new ConsoleChannel(registration, NEXT_COMMAND, exchange -> {
            var response = exchange.response();
            if (!response.hasConsoleCommandWork()) throw new IOException("Missing console command work");
            if (!response.getConsoleCommandWork().hasCommand()) return NEXT_COMMAND;
            var command = response.getConsoleCommandWork().getCommand();
            var work = work(command.getId(), command.getRemainingMs(), exchange);
            var outcome = work.active() ? input.submit(command.getLine(), work) : ConsolePort.CommandOutcome.EXPIRED;
            var receipt = ConsoleCommandReceipt.newBuilder().setId(command.getId()).setOutcome(switch (outcome) {
                case SUBMITTED -> ConsoleCommandReceipt.Outcome.SUBMITTED;
                case EXPIRED -> ConsoleCommandReceipt.Outcome.EXPIRED;
                case UNCONFIRMED -> ConsoleCommandReceipt.Outcome.UNCONFIRMED;
            });
            return commandPoll(ConsoleCommandPoll.newBuilder().setReceipt(receipt).build());
        });
    }

    static ConsoleChannel logs(ConsoleRegistration registration, ConsolePort.LogHistory logs) {
        return new ConsoleChannel(registration, NEXT_LOG_QUERY, exchange -> {
            var response = exchange.response();
            if (!response.hasConsoleLogWork()) throw new IOException("Missing console log work");
            if (!response.getConsoleLogWork().hasRead()) return NEXT_LOG_QUERY;
            var read = response.getConsoleLogWork().getRead();
            var work = work(read.getId(), read.getRemainingMs(), exchange);
            if (!read.hasQuery()) throw new IOException("Missing console log query");
            var query = read.getQuery();
            var position = switch (query.getPositionCase()) {
                case BEFORE -> new ConsolePort.LogQuery(ConsolePort.Position.BEFORE, query.getBefore());
                case AFTER -> new ConsolePort.LogQuery(ConsolePort.Position.AFTER, query.getAfter());
                case POSITION_NOT_SET -> new ConsolePort.LogQuery(ConsolePort.Position.LATEST, "");
            };
            var receipt = ConsoleLogReceipt.newBuilder().setId(read.getId());
            try {
                if (!work.active()) throw new IOException("The console log query expired");
                var page = logs.read(position, work);
                if (!work.active()) throw new IOException("The console log query expired");
                receipt.setPage(ConsoleLogPage.newBuilder().addAllLines(page.lines())
                        .setBefore(page.before()).setAfter(page.after()).setNotice(page.notice()));
            } catch (IOException error) {
                receipt.setError(logError(error));
            }
            return logPoll(ConsoleLogPoll.newBuilder().setReceipt(receipt).build());
        });
    }

    private static Request commandPoll(ConsoleCommandPoll poll) {
        return Request.newBuilder().setConsoleCommandPoll(poll).setTimeoutMs(POLL_TIMEOUT_MS).build();
    }

    private static Request logPoll(ConsoleLogPoll poll) {
        return Request.newBuilder().setConsoleLogPoll(poll).setTimeoutMs(POLL_TIMEOUT_MS).build();
    }

    private static ConsolePort.Work work(String id, int remainingMs, MikdClient.Exchange exchange) throws IOException {
        if (id.isEmpty() || remainingMs < 1 || remainingMs > MAX_WORK_MS) throw new IOException("Invalid console work");
        return new ConsolePort.Work(
                exchange.receivedAt() + TimeUnit.MILLISECONDS.toNanos(remainingMs), exchange.connection()::isOpen);
    }

    private static String logError(IOException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return "Console log unavailable";
        return message.codePoints().limit(128).map(cp -> Character.isISOControl(cp) ? ' ' : cp)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }
}
