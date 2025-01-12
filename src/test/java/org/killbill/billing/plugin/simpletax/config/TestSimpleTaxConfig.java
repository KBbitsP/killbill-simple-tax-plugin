/*
 * Copyright 2015 Benjamin Gandon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.killbill.billing.plugin.simpletax.config;

import static java.math.BigDecimal.ZERO;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.killbill.billing.plugin.simpletax.config.SimpleTaxConfig.DEFAULT_TAX_ITEM_DESC;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.killbill.billing.plugin.simpletax.TaxComputationContext;
import org.killbill.billing.plugin.simpletax.internal.Country;
import org.killbill.billing.plugin.simpletax.internal.TaxCode;
import org.killbill.billing.plugin.simpletax.resolving.InvoiceItemEndDateBasedResolver;
import org.killbill.billing.plugin.simpletax.resolving.NullTaxResolver;
import org.killbill.billing.plugin.simpletax.resolving.TaxResolver;
import org.killbill.billing.plugin.simpletax.resolving.fixtures.InvalidConstructorTaxResolver;
import org.killbill.billing.plugin.simpletax.util.LazyValue;
import org.killbill.billing.test.helpers.TaxCodeBuilder;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;

/**
 * Tests for {@link SimpleTaxConfig}.
 *
 * @author Benjamin Gandon
 */
@SuppressWarnings("javadoc")
public class TestSimpleTaxConfig {

    public static final String TAX_RESOLVER_PROP = "org.killbill.billing.plugin.simpletax.taxResolver";

    private static final Supplier<Constructor<NullTaxResolver>> NTR_CONSTRUCTOR = new LazyValue<Constructor<NullTaxResolver>>() {
        @Override
        protected Constructor<NullTaxResolver> initialize() throws RuntimeException {
            try {
                return NullTaxResolver.class.getConstructor(TaxComputationContext.class);
            } catch (NoSuchMethodException exc) {
                throw new RuntimeException(exc);
            }
        }
    };

    private static final ImmutableMap<String, String> WITH_NON_EXISTING_TAX_RESOLVER = ImmutableMap.of(
            TAX_RESOLVER_PROP, "bad.package.NullTaxResolver");
    private static final ImmutableMap<String, String> WITH_INVALID_TAX_RSOLVER_SUBTYPE = ImmutableMap.of(
            TAX_RESOLVER_PROP, TestSimpleTaxConfig.class.getName());
    private static final ImmutableMap<String, String> WITH_INVALID_TAX_RESOLVER_CONSTRUCTOR = ImmutableMap.of(
            TAX_RESOLVER_PROP, InvalidConstructorTaxResolver.class.getName());
    private static final Map<String, String> WITH_NOOP_TAX_RESOLVER = ImmutableMap.of(TAX_RESOLVER_PROP,
            NullTaxResolver.class.getName());

    private static final Map<String, String> WITH_TAX_CODE_A = ImmutableMap.of(
            "org.killbill.billing.plugin.simpletax.taxCodes.taxA.rate", "0.10");
    private static final Map<String, String> WITH_TAX_CODE_B = ImmutableMap.of(
            "org.killbill.billing.plugin.simpletax.taxCodes.taxB", "");
    private static final Map<String, String> WITH_TAX_CODE_C = ImmutableMap.of(
            "org.killbill.billing.plugin.simpletax.taxCodes.taxC.rate", "0.200",
            "org.killbill.billing.plugin.simpletax.taxCodes.taxC.taxItem.description", "Tax C",
            "org.killbill.billing.plugin.simpletax.taxCodes.taxC.startingOn", "1985-10-25",
            "org.killbill.billing.plugin.simpletax.taxCodes.taxC.stoppingOn", "2015-10-25",
            "org.killbill.billing.plugin.simpletax.taxCodes.taxC.country", "FR");
    private static final ImmutableMap<String, String> WITH_PRODUCT_A = ImmutableMap.of(
            "org.killbill.billing.plugin.simpletax.products.productA", "plop, taxA");
    private static final ImmutableMap<String, String> WITH_PRODUCT_B = ImmutableMap.of(
            "org.killbill.billing.plugin.simpletax.products.productB", "taxA");

    private static final TaxCode TAX_A = new TaxCodeBuilder()//
            .withName("taxA")//
            .withRate(new BigDecimal("0.1"))//
            .withTaxItemDescription(DEFAULT_TAX_ITEM_DESC)//
            .build();
    private static final TaxCode TAX_B = new TaxCodeBuilder()//
            .withName("taxB")//
            .withRate(ZERO)//
            .withTaxItemDescription(DEFAULT_TAX_ITEM_DESC)//
            .build();
    private static final TaxCode TAX_C = new TaxCodeBuilder()//
            .withName("taxC")//
            .withRate(new BigDecimal("0.2"))//
            .withTaxItemDescription("Tax C")//
            .withStartingOn(new LocalDate("1985-10-25"))//
            .withStoppingOn(new LocalDate("2015-10-25"))//
            .withCountry(new Country("FR"))//
            .build();

    @BeforeMethod
    public void init() {
        initMocks(this);
    }

    @Test(groups = "fast")
    public void shouldEarlyWarnOnMissingTaxResolver() {
        // When
        final Logger logger = Mockito.spy(Logger.class);
        SimpleTaxConfig config = new SimpleTaxConfig(ImmutableMap.<String, String> of(), logger);

        // Then
        verify(logger).warn(argThat(allOf(containsString(TAX_RESOLVER_PROP), containsString("should not be blank"))));
        verifyNoMoreInteractions(logger);
        assertEquals(config.getTaxResolverConstructor(), NTR_CONSTRUCTOR.get());
    }

    @Test(groups = "fast")
    public void shouldEarlyComplainOnNonExistingTaxResolverClass() {
        // When
        final Logger logger = Mockito.spy(Logger.class);
        SimpleTaxConfig config = new SimpleTaxConfig(WITH_NON_EXISTING_TAX_RESOLVER, logger);

        // Then
        verify(logger).error(argThat(allOf(containsStringIgnoringCase("cannot load class"), containsString(TAX_RESOLVER_PROP))));
        verifyNoMoreInteractions(logger);
        assertEquals(config.getTaxResolverConstructor(), NTR_CONSTRUCTOR.get());
    }

    @Test(groups = "fast")
    public void shouldEarlyComplainOnNonTaxResolverType() {
        // Given
        Map<String, String> cfg = WITH_INVALID_TAX_RSOLVER_SUBTYPE;
        final Logger logger = Mockito.spy(Logger.class);

        // When
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logger);

        // Then
        InOrder inOrder = inOrder(logger);
        inOrder.verify(logger).error(argThat(allOf(containsStringIgnoringCase("invalid class"), containsString("sub-class"),
                        containsString(TAX_RESOLVER_PROP))));
        inOrder.verify(logger).error(argThat(allOf(containsStringIgnoringCase("invalid class"), containsString("constructor"),
                        containsString(TAX_RESOLVER_PROP))));
        inOrder.verifyNoMoreInteractions();
        assertEquals(config.getTaxResolverConstructor(), NTR_CONSTRUCTOR.get());
    }

    @Test(groups = "fast")
    public void shouldEarlyComplainOnNonExistingTTCConstructor() {
        // Given
        Map<String, String> cfg = WITH_INVALID_TAX_RESOLVER_CONSTRUCTOR;
        final Logger logger = Mockito.spy(Logger.class);

        // When
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logger);

        // Then
        verify(logger).error(argThat(allOf(containsStringIgnoringCase("invalid class"), containsString("constructor"),
                        containsString(TAX_RESOLVER_PROP))));
        verifyNoMoreInteractions(logger);
        assertEquals(config.getTaxResolverConstructor(), NTR_CONSTRUCTOR.get());
    }

    @Test(groups = "fast")
    public void shouldEarlyComplainOnNonExistingTaxCodes() {
        // Given
        Map<String, String> cfg = cfgBuilder()//
                .putAll(WITH_TAX_CODE_A)//
                .putAll(WITH_NOOP_TAX_RESOLVER)//
                .putAll(WITH_PRODUCT_A)//
                .build();
        final Logger logger = Mockito.spy(Logger.class);

        // When
        new SimpleTaxConfig(cfg, logger);

        // Then
        verify(logger).error(argThat(allOf(containsString("org.killbill.billing.plugin.simpletax.products.productA"),
                        containsString("tax code [plop] is not defined"))));
        verifyNoMoreInteractions(logger);
    }

    @Test(groups = "fast")
    public void shouldDefineTaxCodeWithAllProperties() {
        // Given
        Map<String, String> cfg = cfgBuilder()//
                .putAll(WITH_NOOP_TAX_RESOLVER)//
                .putAll(WITH_TAX_CODE_C)//
                .build();
        final Logger logger = Mockito.spy(Logger.class);

        // When
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logger);

        // Then
        assertEquals(config.findTaxCode("taxC"), TAX_C);
        verifyNoMoreInteractions(logger);
    }

    @Test(groups = "fast")
    public void shouldDefineTaxCodeWithNameAndAllDefaultValues() {
        // Given
        Map<String, String> cfg = cfgBuilder()//
                .putAll(WITH_NOOP_TAX_RESOLVER)//
                .putAll(WITH_TAX_CODE_B)//
                .build();
        final Logger logger = Mockito.spy(Logger.class);

        // When
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logger);

        // Then
        assertEquals(config.findTaxCode("taxB"), TAX_B);
        verifyNoMoreInteractions(logger);
    }

    @Test(groups = "fast")
    public void shouldDefineTaxationTimeZone() {
        // Given
        Map<String, String> cfg = cfgBuilder()//
                .putAll(WITH_NOOP_TAX_RESOLVER)//
                .put("org.killbill.billing.plugin.simpletax.taxationTimeZone", "Europe/Paris")//
                .build();
        final Logger logger = Mockito.spy(Logger.class);
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logger);

        // Expect
        assertEquals(config.getTaxationTimeZone(), DateTimeZone.forID("Europe/Paris"));
        verifyNoMoreInteractions(logger);
    }

    @Test(groups = "fast")
    public void shouldDefineTaxAmountPrecision() {
        // Given
        Map<String, String> cfg = cfgBuilder()//
                .putAll(WITH_NOOP_TAX_RESOLVER)//
                .put("org.killbill.billing.plugin.simpletax.taxItem.amount.precision", "7")//
                .build();
        final Logger logger = Mockito.spy(Logger.class);
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logger);

        // Expect
        assertEquals(config.getTaxAmountPrecision(), 7);
        verifyNoMoreInteractions(logger);
    }

    @Test(groups = "fast")
    public void shouldReturnTaxResolverConstructor() throws Exception {
        // Given
        Map<String, String> cfg = ImmutableMap.of(TAX_RESOLVER_PROP, InvoiceItemEndDateBasedResolver.class.getName());
        final Logger logger = Mockito.spy(Logger.class);
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logger);

        // When
        Constructor<? extends TaxResolver> constructor = config.getTaxResolverConstructor();

        // Then
        assertEquals(constructor, InvoiceItemEndDateBasedResolver.class.getConstructor(TaxComputationContext.class));
        verifyNoMoreInteractions(logger);
    }

    @Test(groups = "fast")
    public void shouldReturnedEmptyConfiguredTaxCode() {
        // Given
        Map<String, String> cfg = cfgBuilder().putAll(WITH_NOOP_TAX_RESOLVER)//
                .build();
        final Logger logger = Mockito.spy(Logger.class);
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logger);

        // When
        Set<TaxCode> taxCodes = config.getConfiguredTaxCodes("non-existing-product");

        // Then
        assertEquals(taxCodes, ImmutableSet.of());
        verifyNoMoreInteractions(logger);
    }

    @Test(groups = "fast")
    public void shouldReturnedConfiguredTaxCode() {
        // Given
        Map<String, String> cfg = cfgBuilder().putAll(WITH_NOOP_TAX_RESOLVER)//
                .putAll(WITH_TAX_CODE_A)//
                .putAll(WITH_PRODUCT_B)//
                .build();
        final Logger logger = Mockito.spy(Logger.class);
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logger);

        // When
        Set<TaxCode> taxCodes = config.getConfiguredTaxCodes("productB");

        // Then
        assertEquals(taxCodes, ImmutableSet.of(TAX_A));
        verifyNoMoreInteractions(logger);
    }

    @Test(groups = "fast")
    public void shouldReturnedConfiguredTaxCodeAndComplainForUndefinedTaxCode() {
        // Given
        Map<String, String> cfg = cfgBuilder().putAll(WITH_NOOP_TAX_RESOLVER)//
                .putAll(WITH_TAX_CODE_A)//
                .putAll(WITH_PRODUCT_A)//
                .build();
        final Logger logger = Mockito.spy(Logger.class);
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logger);
        reset(logger);

        // When
        Set<TaxCode> taxCodes = config.getConfiguredTaxCodes("productA");

        // Then
        assertEquals(taxCodes, ImmutableSet.of(TAX_A));
        verify(logger).error(argThat(allOf(containsString("plop"), containsString("is undefined"))));
        verifyNoMoreInteractions(logger);
    }

    @Test(groups = "fast")
    public void shouldReturnedTaxCodesOrComplain() {
        // Given
        Map<String, String> cfg = cfgBuilder().putAll(WITH_NOOP_TAX_RESOLVER)//
                .putAll(WITH_TAX_CODE_A)//
                .build();
        final Logger logger = Mockito.spy(Logger.class);
        SimpleTaxConfig config = new SimpleTaxConfig(cfg, logger);
        reset(logger);

        // When
        Set<TaxCode> taxCodes = config.findTaxCodes("taxA, bim", "from plop");

        // Then
        assertEquals(taxCodes, ImmutableSet.of(TAX_A));
        verify(logger).error(argThat(allOf(containsString("bim"), containsString("from plop"), containsString("is undefined"))));
        verifyNoMoreInteractions(logger);
    }

    private static Builder<String, String> cfgBuilder() {
        return ImmutableMap.<String, String> builder();
    }
}
