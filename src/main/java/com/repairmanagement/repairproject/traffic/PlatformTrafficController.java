package com.repairmanagement.repairproject.traffic;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** The Spring Security /api/platform/** rule restricts these IP details to SUPER_ADMIN. */
@RestController
@RequestMapping("/api/platform/traffic")
public class PlatformTrafficController {
    private final JdbcTemplate jdbc;
    public PlatformTrafficController(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record DailyIp(String day, String ip, long requests, long inboundBytes,
                          long knownOutboundBytes, String lastSeenUtc) {}
    @GetMapping("/daily")
    public List<DailyIp> daily(@RequestParam(required=false) LocalDate date) {
        LocalDate requested = date==null? LocalDate.now(ZoneOffset.UTC):date;
        if (requested.isBefore(LocalDate.now(ZoneOffset.UTC).minusDays(6)) ||
            requested.isAfter(LocalDate.now(ZoneOffset.UTC)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select a date within the last 7 days (UTC)");
        return jdbc.query("""
            SELECT request_day,ip_address,request_count,inbound_bytes,outbound_bytes_known,last_seen_at
            FROM daily_http_traffic WHERE request_day=?
            ORDER BY request_count DESC LIMIT 500
            """,(rs,n)->new DailyIp(rs.getDate(1).toLocalDate().toString(),rs.getString(2),
                rs.getLong(3),rs.getLong(4),rs.getLong(5),rs.getTimestamp(6).toLocalDateTime().toString()),requested);
    }
}
