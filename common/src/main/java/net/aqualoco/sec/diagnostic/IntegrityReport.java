package net.aqualoco.sec.diagnostic;

import java.util.List;

public record IntegrityReport(IntegrityHealth health,
                              String summary,
                              List<String> details) {
}
