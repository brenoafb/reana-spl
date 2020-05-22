package tool;

import org.junit.Test;

import junit.framework.Assert;

import tool.analyzers.strategies.FeatureFamilyProductBasedAnalyzer;

public class VariabilityEncodingTest {
	@Test
	public void testSubstitution() {
		String exp = "x";
		String subs = "y+1";
		String expected = "y+1";
		String newExp = FeatureFamilyProductBasedAnalyzer.substitute("x", subs, exp);
		
        Assert.assertEquals(expected, newExp);
        
        exp = "x + y + 2";
        subs = "y + 3";
        expected = "y + 3 + y + 2";
        newExp = FeatureFamilyProductBasedAnalyzer.substitute("x", subs, exp);

        Assert.assertEquals(expected, newExp);
        
        exp = "x0 + x1 + x10 + ax1";
        subs = "x";
        expected = "x0 + x + x10 + ax1";
        newExp = FeatureFamilyProductBasedAnalyzer.substitute("x1", subs, exp);

        Assert.assertEquals(expected, newExp);
	}
}
