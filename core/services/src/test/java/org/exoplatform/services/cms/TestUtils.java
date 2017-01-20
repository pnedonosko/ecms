package org.exoplatform.services.cms;


import org.exoplatform.services.cms.impl.Utils;
import org.junit.Assert;
import org.junit.Test;


public class TestUtils {

    @Test
    public void testCleanString(){
        String cleanName = Utils.cleanString("test_file") ;
        Assert.assertEquals("test_file", cleanName);

        cleanName = Utils.cleanString(" test_file") ;
        Assert.assertEquals("test_file", cleanName);

        cleanName = Utils.cleanString("c'est-mon_file") ;
        Assert.assertEquals("cest-mon_file", cleanName);

        cleanName = Utils.cleanString("éà-abc") ;
        Assert.assertEquals("ea-abc", cleanName);


        cleanName = Utils.cleanString("&abc~abc") ;
        Assert.assertEquals("abcabc", cleanName);
    }
}
