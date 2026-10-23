package moze_intel.projecte.emc;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecipeEmcValidatorTest {
    @Test void seedsAreUpperBoundsAcrossFractionalAndTagConversions() {
        var mapper = new SimpleGraphMapper<String, org.apache.commons.math3.fraction.BigFraction, moze_intel.projecte.api.mapper.arithmetic.IValueArithmetic<org.apache.commons.math3.fraction.BigFraction>>(
                new moze_intel.projecte.emc.arithmetic.HiddenBigFractionArithmetic(), true);
        var collector = new moze_intel.projecte.emc.collector.LongToBigFractionCollector<>(mapper);
        collector.setValueBefore("ingot", 256L);
        collector.setValueBefore("ingotTag", 256L);
        collector.setValueBefore("expensiveSource", 300L);
        collector.setValueFromConversion(1, "ingotTag", java.util.List.of("expensiveSource"));
        collector.addConversion(9, "nugget", java.util.List.of("ingot"));
        collector.addConversion(1, "ingot", java.util.Collections.nCopies(9, "nugget"));
        collector.addConversion(1, "ingotTag", java.util.List.of("ingot"));
        collector.addConversion(1, "dust", java.util.List.of("ingotTag"));
        var values = new moze_intel.projecte.emc.generator.BigFractionToLongGenerator<>(mapper).generateValues();
        assertEquals(28, values.getLong("nugget"));
        assertEquals(252, values.getLong("ingot"));
        assertEquals(252, values.getLong("dust"));
    }
    @Test void sharedOutputCost() {
        assertEquals(74, RecipeEmcValidator.affordableUnit(BigInteger.valueOf(224), BigInteger.ZERO, 3));
    }
    @Test void returnedToolIsNotConsumed() {
        assertEquals(33, RecipeEmcValidator.affordableUnit(BigInteger.valueOf(564), BigInteger.valueOf(531), 1));
    }
    @Test void negativeAndFreeCostsCannotGenerateEmc() {
        assertEquals(0, RecipeEmcValidator.affordableUnit(BigInteger.valueOf(10), BigInteger.valueOf(11), 1));
        assertEquals(0, RecipeEmcValidator.affordableUnit(BigInteger.TEN, BigInteger.TEN, 1));
    }
    @Test void intermediatesDoNotOverflow() {
        BigInteger max = BigInteger.valueOf(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, RecipeEmcValidator.affordableUnit(max.multiply(BigInteger.TEN), max, 9));
    }
    @Test void invalidOutputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RecipeEmcValidator.affordableUnit(BigInteger.ONE, BigInteger.ZERO, 0));
    }
}
