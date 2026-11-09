package com.repairmanagement.repairproject.traffic;

import java.net.InetAddress;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Request-level HTTP metrics, not packet captures or complete VPS network bandwidth. */
@Component
public class TrafficRecorder {
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9a-fA-F:.]{2,45}");
    private final JdbcTemplate jdbc;
    private final boolean trustCaddy;
    private final ConcurrentHashMap<Key,Counts> pending = new ConcurrentHashMap<>();
    public TrafficRecorder(JdbcTemplate jdbc,
        @Value("${APP_TRAFFIC_TRUST_CADDY_REAL_IP:false}") boolean trustCaddy) {
        this.jdbc=jdbc; this.trustCaddy=trustCaddy;
    }
    record Key(LocalDate day, String ip) {}
    static final class Counts {
        final AtomicLong requests=new AtomicLong();
        final AtomicLong incoming=new AtomicLong();
        final AtomicLong outgoing=new AtomicLong();
        volatile LocalDateTime lastSeen;
        void add(long received,long sent,LocalDateTime when) {
            requests.incrementAndGet(); incoming.addAndGet(Math.max(0,received));
            outgoing.addAndGet(Math.max(0,sent)); lastSeen=when;
        }
    }
    public void record(String peer, String caddyRealIp, long requestBytes, long responseBytes) {
        String source = trustCaddy && caddyRealIp != null ? caddyRealIp : peer;
        String ip = normalizeIp(source);
        if (ip == null) return;
        var now=LocalDateTime.now(ZoneOffset.UTC);
        var key=new Key(now.toLocalDate(),ip);
        // Bound memory under unexpected traffic; protect availability of the main application.
        if (pending.size()>10000 && !pending.containsKey(key)) return;
        pending.compute(key,(k,current)->{
            Counts c=current==null?new Counts():current;
            c.add(requestBytes,responseBytes,now);
            return c;
        });
    }
    static String normalizeIp(String raw) {
        if (raw==null || !IP_LITERAL.matcher(raw).matches()) return null;
        try { return InetAddress.getByName(raw).getHostAddress(); }
        catch (Exception ex) { return null; }
    }
    @Scheduled(fixedDelayString="${app.traffic.flush-ms:60000}")
    public void flush() {
        var snapshots=new ArrayList<>(pending.entrySet());
        for(var entry:snapshots) {
            Counts c=entry.getValue();
            // Remove first; if a database call fails, restore the captured totals.
            if (!pending.remove(entry.getKey(),c)) continue;
            long requests=c.requests.get(), inbound=c.incoming.get(), outbound=c.outgoing.get();
            try {
                jdbc.update("""
                  INSERT INTO daily_http_traffic(request_day,ip_address,request_count,inbound_bytes,
                      outbound_bytes_known,last_seen_at)
                  VALUES (?,?,?,?,?,?)
                  ON DUPLICATE KEY UPDATE
                    request_count=request_count+?,
                    inbound_bytes=inbound_bytes+?, outbound_bytes_known=outbound_bytes_known+?,
                    last_seen_at=GREATEST(last_seen_at,?)
                """, entry.getKey().day(),entry.getKey().ip(),requests,inbound,outbound,c.lastSeen,
                    requests,inbound,outbound,c.lastSeen);
            } catch(Exception ex) {
                pending.compute(entry.getKey(),(k,existing)->{
                    if (existing==null) return c;
                    existing.add(0,0,c.lastSeen);
                    existing.requests.addAndGet(requests-1);
                    existing.incoming.addAndGet(inbound);
                    existing.outgoing.addAndGet(outbound);
                    return existing;
                });
                // Do not allow monitoring failure to interrupt business requests.
            }
        }
        // 7-day retention. Avoid cleanups during every HTTP request.
        try { jdbc.update("DELETE FROM daily_http_traffic WHERE request_day < ?",
            LocalDate.now(ZoneOffset.UTC).minusDays(6)); }
        catch(Exception ignored) { }
    }
}
