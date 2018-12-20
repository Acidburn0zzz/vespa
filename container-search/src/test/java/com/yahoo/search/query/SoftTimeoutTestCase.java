// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.yahoo.prelude.query.QueryException;
import com.yahoo.search.Query;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author baldersheim
 */
public class SoftTimeoutTestCase {
    @Test
    public void testDefaultsInQuery() {
        Query query=new Query("?query=test");
        assertTrue(query.getRanking().getSoftTimeout().getEnable());
        assertNull(query.getRanking().getSoftTimeout().getFactor());
        assertNull(query.getRanking().getSoftTimeout().getTailcost());
    }

    @Test
    public void testQueryOverride() {
        Query query=new Query("?query=test&ranking.softtimeout.factor=0.7&ranking.softtimeout.tailcost=0.3");
        assertTrue(query.getRanking().getSoftTimeout().getEnable());
        assertEquals(Double.valueOf(0.7), query.getRanking().getSoftTimeout().getFactor());
        assertEquals(Double.valueOf(0.3), query.getRanking().getSoftTimeout().getTailcost());
        query.prepare();
        assertEquals("true", query.getRanking().getProperties().get("vespa.softtimeout.enable").get(0));
        assertEquals("0.7", query.getRanking().getProperties().get("vespa.softtimeout.factor").get(0));
        assertEquals("0.3", query.getRanking().getProperties().get("vespa.softtimeout.tailcost").get(0));
    }

    @Test
    public void testDisable() {
        Query query=new Query("?query=test&ranking.softtimeout.enable=false");
        assertFalse(query.getRanking().getSoftTimeout().getEnable());
        query.prepare();
        assertTrue(query.getRanking().getProperties().isEmpty());
    }

    private void verifyException(String key, String value) {
        try {
            new Query("?query=test&ranking.softtimeout."+key+"="+value);
            assertFalse(true);
        } catch (QueryException e) {
            assertEquals("Invalid request parameter", e.getMessage());
            assertEquals("Could not set 'ranking.softtimeout." + key + "' to '" + value +"'", e.getCause().getMessage());
            assertEquals(key + " must be in the range [0.0, 1.0], got " + value, e.getCause().getCause().getMessage());
        }
    }
    @Test
    public void testLimits() {
        verifyException("factor", "-0.1");
        verifyException("factor", "1.1");
        verifyException("tailcost", "-0.1");
        verifyException("tailcost", "1.1");
    }
}
