package com.repairmanagement.repairproject.traffic;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TrafficRecorderTest {
    @Test void rejectHostnamesAndInvalidIps(){
        assertNull(TrafficRecorder.normalizeIp("example.com"));
        assertNull(TrafficRecorder.normalizeIp("1.2.3.4, 5.6.7.8"));
        assertNull(TrafficRecorder.normalizeIp("127.0.0.1\r\nOther: yes"));
    }
    @Test void acceptNumericIpLiterals(){
        assertEquals("127.0.0.1",TrafficRecorder.normalizeIp("127.0.0.1"));
        assertNotNull(TrafficRecorder.normalizeIp("::1"));
    }
}
