/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.integration.test.java.controller;

import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.auraframework.cache.Cache;
import org.auraframework.components.test.java.controller.CustomParamType;
import org.auraframework.def.ActionDef;
import org.auraframework.def.ComponentDef;
import org.auraframework.def.ControllerDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.DefDescriptor.DescriptorKey;
import org.auraframework.def.Definition;
import org.auraframework.def.TypeDef;
import org.auraframework.impl.AuraImplTestCase;
import org.auraframework.impl.java.controller.JavaAction;
import org.auraframework.impl.java.controller.JavaActionDef;
import org.auraframework.impl.java.model.JavaValueDef;
import org.auraframework.instance.Action;
import org.auraframework.instance.Action.State;
import org.auraframework.integration.test.logging.LoggingTestAppender;
import org.auraframework.service.CachingService;
import org.auraframework.service.ServerService;
import org.auraframework.system.Location;
import org.auraframework.system.LoggingContext.KeyValueLogger;
import org.auraframework.system.Message;
import org.auraframework.test.controller.TestLoggingAdapterController;
import org.auraframework.test.source.StringSourceLoader;
import org.auraframework.throwable.AuraUnhandledException;
import org.auraframework.throwable.NoAccessException;
import org.auraframework.throwable.quickfix.DefinitionNotFoundException;
import org.auraframework.throwable.quickfix.InvalidDefinitionException;
import org.auraframework.throwable.quickfix.QuickFixException;
import org.auraframework.util.test.annotation.ThreadHostileTest;
import org.auraframework.util.test.annotation.UnAdaptableTest;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Automation for java Controllers.
 */
public class JavaControllerTest extends AuraImplTestCase {

    @Inject
    private CachingService cachingService;

    @Inject
    private ServerService serverService;
    
    private Logger logger;
    private LoggingTestAppender appender;
    private Level originalLevel;
    
    @Override
    public void setUp() throws Exception {
        super.setUp();
        appender = new LoggingTestAppender();

        logger = Logger.getLogger("LoggingContextImpl");
        // When we run integration tests, the logging level of logger LoggingContextImpl
        // is WARN, setting it into INFO here so that we can get the log as we run the app.
        originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
    }
    
    @Override
    public void tearDown() throws Exception {
        logger.removeAppender(appender);
        logger.setLevel(originalLevel);
        super.tearDown();
    }

    private ControllerDef getJavaController(String name) throws Exception {
        DefDescriptor<ControllerDef> javaCntrlrDefDesc = definitionService.getDefDescriptor(name, ControllerDef.class);
        return javaCntrlrDefDesc.getDef();
    }

    private void assertControllerThrows(String name, Class<? extends Exception> clazz, String start, String loc) {
        DefDescriptor<ControllerDef> javaCntrlrDefDesc = definitionService.getDefDescriptor(name, ControllerDef.class);

        try {
            javaCntrlrDefDesc.getDef();
            fail("Expected " + clazz.getName());
        } catch (Exception e) {
            this.checkExceptionStart(e, clazz, start, loc);
        }
    }

    private void checkPassAction(ControllerDef controller, String name, Map<String, Object> args, State expState,
                                 Object returnValue) throws QuickFixException {

        ActionDef actionDef = controller.getSubDefinition(name);
        Action action = instanceService.getInstance(actionDef, args);

        action.run();
        assertEquals(name + " State", expState, action.getState());
        assertEquals(name + " expected no errors", 0, action.getErrors().size());
        assertEquals(name + " return", returnValue, action.getReturnValue());
    }

    private void checkFailAction(ControllerDef controller, String name, Map<String, Object> args, State expState,
                                 Class<? extends Exception> error, String errorMessage) throws QuickFixException {
        ActionDef actionDef = controller.getSubDefinition(name);
        Action action = instanceService.getInstance(actionDef, args);
        action.run();
        assertEquals(name + " State", expState, action.getState());
        assertEquals(name + " expected an error", 1, action.getErrors().size());
        checkExceptionContains((Exception) action.getErrors().get(0), error, errorMessage);
        assertEquals(name + " return", null, action.getReturnValue());
    }

    /**
     * Verify that controller implements {@link org.auraframework.ds.servicecomponent.Controller}.
     */
    @Test
    public void testControllerImplements() throws Exception {
        assertControllerThrows(
                "java://org.auraframework.impl.java.controller.TestControllerWithoutImplements",
                InvalidDefinitionException.class,
                "class org.auraframework.impl.java.controller.TestControllerWithoutImplements must implement org.auraframework.ds.servicecomponent.Controller",
                "org.auraframework.impl.java.controller.TestControllerWithoutImplements"
        );
    }

    /**
     * Ensure that a key is required for every parameter.
     */
    @Test
    public void testMissingKeyAnnotation() throws Exception {
        assertControllerThrows("java://org.auraframework.impl.java.controller.TestControllerMissingKey",
                InvalidDefinitionException.class, "@Key annotation is required on all action parameters",
                "org.auraframework.impl.java.controller.TestControllerMissingKey.appendStrings");
    }

    /**
     * Ensure that an action must be public. Currently, we do not actualy process non-public members. This is due to a
     * limitation in the way java returns methods. If we do want to do this, we'd have to process all methods in a
     * rather complex way (walking up the class hierarchy).
     */
    @Test
    public void testProtectedAction() throws Exception {
        ControllerDef cont = getJavaController("java://org.auraframework.impl.java.controller.TestControllerWithProtectedAction");

        assertNotNull("could not find controller", cont);
        assertNull("should not have appendStrings", cont.getActionDefs().get("appendStrings"));
        assertNull("should not have doSomething", cont.getActionDefs().get("doSomething"));
        assertEquals("should have one method", 1, cont.getActionDefs().size());
        assertNotNull("should have doNothing", cont.getActionDefs().get("doNothing"));
    }

    @Test
    public void testActionNoParameters() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.components.test.java.controller.TestController");
        Map<String, Object> empty = new HashMap<>();
        Map<String, Object> hasOne = new HashMap<>();
        hasOne.put("a", "don't care");
        assertNotNull("unable to load test controller", controller);

        checkPassAction(controller, "doSomething", empty, State.SUCCESS, null);
        checkPassAction(controller, "doSomething", hasOne, State.SUCCESS, null);
        checkPassAction(controller, "getString", empty, State.SUCCESS, "TestController");
        checkFailAction(controller, "throwException", empty, State.ERROR, AuraUnhandledException.class,
                "java://org.auraframework.components.test.java.controller.TestController: java.lang.RuntimeException: intentionally generated");
    }

    /**
     * Verify correct errors are thrown when invalid parameters are passed to the controller.
     */
    @Test
    public void testActionWithParametersError() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.impl.java.controller.TestControllerWithParameters");
        Map<String, Object> args = new HashMap<>();

        // A custom type parameter without a converter for what's passed to it (String)
        args.put("a", "x");
        checkFailAction(controller, "customParam", args, State.ERROR, AuraUnhandledException.class,
                "Error on parameter a: java://org.auraframework.impl.java.controller.TestControllerWithParameters$CustomParam");

        // No parameters to a controller method that requires params
        args.clear();
        checkFailAction(controller, "sumValues", args, State.ERROR, AuraUnhandledException.class,
                "java://org.auraframework.impl.java.controller.TestControllerWithParameters: java.lang.NullPointerException");

        // Passing the wrong type (Strings instead of Integers)
        args.put("a", "x");
        args.put("b", "y");
        checkFailAction(controller, "sumValues", args, State.ERROR, AuraUnhandledException.class,
                "Invalid value for a: java://java.lang.Integer");
    }

    /**
     * Test to ensure that parameters get passed correctly.
     */
    @Test
    public void testActionWithParameters() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.impl.java.controller.TestControllerWithParameters");
        Map<String, Object> args = new HashMap<>();

        args.put("a", "x");
        args.put("b", "y");
        checkPassAction(controller, "appendStrings", args, State.SUCCESS, "xy");

        // Is this correct?
        args.clear();
        checkPassAction(controller, "appendStrings", args, State.SUCCESS, "nullnull");

        args.put("a", new Integer(1));
        args.put("b", new Integer(2));
        checkPassAction(controller, "sumValues", args, State.SUCCESS, new Integer(3));

        args.put("a", "1");
        args.put("b", "2");
        checkPassAction(controller, "sumValues", args, State.SUCCESS, new Integer(3));

    }

    /**
     * This is testing JavaAction with parameter that throws QFE when accessing. verify AuraUnhandledException is added
     * when this happen in JavaAction
     */
    @Test
    public void testActionWithBadParameterThrowsQFE() throws Exception {
        // create DefDescriptor for JavaValueDefExt, type doesn't matter as we plan to spy on it.
        String instanceName = "java://java.lang.String";
        DefDescriptor<TypeDef> JavaValueDefDesc = definitionService.getDefDescriptor(instanceName, TypeDef.class);
        // spy on DefDescriptor, ask it to throw QFE when calling getDef()
        DefDescriptor<TypeDef> JavaValueDefDescMocked = Mockito.spy(JavaValueDefDesc);
        Mockito.when(JavaValueDefDescMocked.getDef()).thenThrow(new TestQuickFixException("new quick fix exception"));
        // time to ask MDR give us what we want
        String name = "java://org.auraframework.integration.test.java.controller.JavaControllerTest$JavaValueDefExt";
        Class<TypeDef> defClass = TypeDef.class;
        DescriptorKey dk = new DescriptorKey(name, defClass);
        Cache<DescriptorKey, DefDescriptor<? extends Definition>> cache =
                cachingService.getDefDescriptorByNameCache();
        cache.put(dk, JavaValueDefDescMocked);

        // jvd doesn't matter that much for triggering QFE, as we only used it as the Object param
        JavaValueDef jvd = new JavaValueDef("tvdQFE", JavaValueDefDesc, null);
        Map<String, Object> args = new HashMap<>();
        args.put("keya", jvd);
        ControllerDef controller = getJavaController("java://org.auraframework.integration.test.java.controller.TestControllerOnlyForJavaControllerTest");

        // we actually catch the QFE in JavaAction.getArgs(), then wrap it up with AuraUnhandledException
        String errorMsg = "Invalid parameter keya: java://java.lang.String";
        checkFailAction(controller, "customErrorParam", args, State.ERROR, AuraUnhandledException.class,
                errorMsg);

    }

    @SuppressWarnings("serial")
    public class JavaValueDefExt extends JavaValueDef {
        public JavaValueDefExt(String name,
                DefDescriptor<TypeDef> typeDescriptor, Location location) {
            super(name, typeDescriptor, location);
        }
    }

    private static class TestQuickFixException extends QuickFixException {
        private static final long serialVersionUID = 7887234381181710432L;

        public TestQuickFixException(String name) {
            super(name, null);
        }
    }

    /**
     * Verify that nice exception is thrown if controller def doesn't exist
     */
    @Test
    public void testControllerNotFound() throws Exception {
        DefDescriptor<ComponentDef> dd = addSourceAutoCleanup(ComponentDef.class,
                "<aura:component controller='java://goats'/>");
        try {
            instanceService.getInstance(dd);
            fail("Expected DefinitionNotFoundException");
        } catch (DefinitionNotFoundException e) {
            assertTrue("Missing error message in "+e.getMessage(),
                    e.getMessage().startsWith("No CONTROLLER named java://goats found"));
        }
    }

    /**
     * Verify controller can be accessed in system namespace
     */
    @Test
    public void testControllerInSystemNamespace() throws Exception {
        String resourceSource = "<aura:component controller='java://org.auraframework.components.test.java.controller.TestController'>Hello World!</aura:component>";

        DefDescriptor<? extends Definition> dd = getAuraTestingUtil().addSourceAutoCleanup(ComponentDef.class,
                resourceSource,
                StringSourceLoader.DEFAULT_NAMESPACE + ":testComponent", true);

        try {
            instanceService.getInstance(dd);
        } catch (NoAccessException e) {
            fail("Not Expected NoAccessException");
        }
    }

    /**
     * Verify controller can not be accessed in custom namespace
     */
    @Test
    @UnAdaptableTest("namespace start with c means something special in core")
    public void testControllerInCustomNamespace() throws Exception {
        String resourceSource = "<aura:component controller='java://org.auraframework.components.test.java.controller.TestController'>Hello World!</aura:component>";

        DefDescriptor<? extends Definition> dd = getAuraTestingUtil().addSourceAutoCleanup(ComponentDef.class,
                resourceSource,
                StringSourceLoader.DEFAULT_CUSTOM_NAMESPACE + ":testComponent", false);

        try {
            instanceService.getInstance(dd);
            fail("Expected NoAccessException");
        } catch (NoAccessException e) {
            String errorMessage = "Access to controller 'org.auraframework.components.test.java.controller:TestController' from namespace '"
                    + StringSourceLoader.DEFAULT_CUSTOM_NAMESPACE
                    + "' in '"
                    + dd.getQualifiedName()
                    + "(COMPONENT)' disallowed by MasterDefRegistry.assertAccess()";
            assertEquals(errorMessage, e.getMessage());
        }
    }

    @Test
    public void testDuplicateAction() throws Exception {
        assertControllerThrows("java://org.auraframework.impl.java.controller.TestControllerWithDuplicateAction",
                InvalidDefinitionException.class, "Duplicate action appendStrings",
                "org.auraframework.impl.java.controller.TestControllerWithDuplicateAction");
    }

    @Test
    public void testGetSubDefinition() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.components.test.java.controller.TestController");
        ActionDef subDef = controller.getSubDefinition("getString");
        assertEquals("SubDefinition is the wrong type", ActionDef.ActionType.SERVER, subDef.getActionType());
        assertEquals("java://org.auraframework.components.test.java.controller.TestController/ACTION$getString", subDef
                .getDescriptor().getQualifiedName());
    }

    @Test
    public void testGetNullSubDefinition() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.components.test.java.controller.TestController");
        ActionDef subDefNonExistent = controller.getSubDefinition("iDontExist");
        assertNull("Trying to retrieve non-existent subdefiniton should return null", subDefNonExistent);
    }

    /**
     * Tests to verify the APIs on Action to mark actions as storable.
     */
    @Test
    public void testStorable() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.components.test.java.controller.TestController");

        ActionDef actionDef = controller.getSubDefinition("getString");
        Action freshAction = instanceService.getInstance(actionDef, null);

        assertTrue("Expected an instance of JavaAction", freshAction instanceof JavaAction);
        JavaAction action = (JavaAction) freshAction;
        assertFalse("Actions should not be storable by default.", action.isStorable());
        action.run();
        assertFalse("isStorabel should not change values after action execution.", action.isStorable());

        Action storableAction = instanceService.getInstance(actionDef, null);
        action = (JavaAction) storableAction;
        action.setStorable();
        assertTrue("Failed to mark a action as storable.", action.isStorable());
        action.run();
        assertTrue("Storable action was unmarked during execution", action.isStorable());
    }

    /**
     * Action without annotation is not backgroundable
     */
    @Test
    public void testJavaActionDefIsBackgroundWithoutAnnotation() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.impl.java.controller.ParallelActionTestController");
        ActionDef actionDef = controller.getActionDefs().get("executeInForeground");
        assertFalse("ActionDefs should not be backgroundable without BackgroundAction annotation",
                ((JavaActionDef) actionDef).isBackground());
    }

    /**
     * Action without annotation is not backgroundable
     */
    @Test
    public void testJavaActionDefIsBackgroundWithAnnotation() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.impl.java.controller.ParallelActionTestController");
        ActionDef actionDef = controller.getActionDefs().get("executeInBackground");
        assertTrue("ActionDefs should be backgroundable with BackgroundAction annotation",
                ((JavaActionDef) actionDef).isBackground());
    }

    @Test
    public void testSerialize() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.impl.java.controller.ParallelActionTestController");
        serializeAndGoldFile(controller);
    }

    /**
     * Tests to verify the logging of params
     */
    @Test
    public void testParamLogging() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.components.test.java.controller.JavaTestController");

        ActionDef getStringActionDef = controller.getSubDefinition("getString");
        JavaAction nonLoggableStringAction = instanceService.getInstance(getStringActionDef, null);

        ActionDef getIntActionDef = controller.getSubDefinition("getInt");
        JavaAction nonLoggableIntAction = instanceService.getInstance(getIntActionDef, null);

        ActionDef getLoggableStringActionDef = controller.getSubDefinition("getLoggableString");
        JavaAction loggableStringAction = instanceService.getInstance(getLoggableStringActionDef,
                Collections.singletonMap("param", (Object) "bar"));

        JavaAction loggableIntAction = instanceService.getInstance(getLoggableStringActionDef,
                Collections.singletonMap("param", (Object) 1));
        JavaAction loggableNullAction = instanceService.getInstance(getLoggableStringActionDef,
                Collections.singletonMap("param", null));

        TestLogger testLogger = new TestLogger();

        nonLoggableStringAction.logParams(testLogger);
        assertNull("Key should not have been logged", testLogger.key);
        assertNull("Value should not have been logged", testLogger.value);

        nonLoggableIntAction.logParams(testLogger);
        assertNull("Key should not have been logged", testLogger.key);
        assertNull("Value should not have been logged", testLogger.value);

        loggableStringAction.logParams(testLogger);
        assertEquals("Key was not logged", "param", testLogger.key);
        assertEquals("Value was not logged", "bar", testLogger.value);

        loggableIntAction.logParams(testLogger);
        assertEquals("Key was not logged", "param", testLogger.key);
        assertEquals("Value was not logged", "1", testLogger.value);

        loggableNullAction.logParams(testLogger);
        assertEquals("Key was not logged", "param", testLogger.key);
        assertEquals("Value was not logged", "null", testLogger.value);
    }

    @ThreadHostileTest("TestLoggingAdapter not thread-safe")
    @Test
    @UnAdaptableTest("W-2928878, we don't have test logging adapter in core")
    public void testParamLogging_NoParams() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.components.test.java.controller.TestController");
        Map<String, Object> params = Maps.newHashMap();
        ActionDef actionDef = controller.getSubDefinition("getString");
        Action nonLoggableStringAction = instanceService.getInstance(actionDef, params);
        Set<String> logsSet = runActionsAndReturnLogs(Lists.newArrayList(nonLoggableStringAction));
        assertEquals(1, logsSet.size());
        assertTrue(
                "Failed to log a server action",
                logsSet.contains(
                        "action_1$java://org.auraframework.components.test.java.controller.TestController/ACTION$getString"));
    }

    @ThreadHostileTest("TestLoggingAdapter not thread-safe")
    @Test
    @UnAdaptableTest("W-2928878, we don't have test logging adapter in core")
    public void testParamLogging_SelectParameters() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.components.test.java.controller.JavaTestController");
        Map<String, Object> params = Maps.newHashMap();
        params.put("strparam", "BoogaBoo");
        params.put("intparam", 1);
        ActionDef actionDef = controller.getSubDefinition("getSelectedParamLogging");
        Action selectParamLoggingAction = instanceService.getInstance(actionDef, params);
        Set<String> logsSet = runActionsAndReturnLogs(Lists.newArrayList(selectParamLoggingAction));
        assertEquals(1, logsSet.size());
        assertTrue(
                "Failed to log a server action and selected parameter assignment",
                logsSet.contains(
                        "action_1$java://org.auraframework.components.test.java.controller.JavaTestController/ACTION$getSelectedParamLogging{strparam,BoogaBoo}"));
    }

    @ThreadHostileTest("TestLoggingAdapter not thread-safe")
    @Test
    @UnAdaptableTest("W-2928878, we don't have test logging adapter in core")
    public void testParamLogging_MultipleIdenticalActions() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.components.test.java.controller.JavaTestController");
        Map<String, Object> params1 = Maps.newHashMap();
        params1.put("strparam", "BoogaBoo");
        params1.put("intparam", 1);

        ActionDef actionDef = controller.getSubDefinition("getSelectedParamLogging");
        Action selectParamLoggingAction1 = instanceService.getInstance(actionDef, params1);

        Map<String, Object> params2 = Maps.newHashMap();
        params2.put("strparam", "BoogaBoo");
        params2.put("intparam", 1);
        Action selectParamLoggingAction2 = instanceService.getInstance(actionDef, params2);

        Set<String> logsSet = runActionsAndReturnLogs(Lists.newArrayList(selectParamLoggingAction1, selectParamLoggingAction2));
        assertEquals(2, logsSet.size());
        assertTrue(
                "Failed to log first server action and selected parameter assignment",
                logsSet.contains(
                        "action_1$java://org.auraframework.components.test.java.controller.JavaTestController/ACTION$getSelectedParamLogging{strparam,BoogaBoo}"));
        assertTrue(
                "Failed to log second server action and selected parameter assignment",
                logsSet.contains(
                        "action_2$java://org.auraframework.components.test.java.controller.JavaTestController/ACTION$getSelectedParamLogging{strparam,BoogaBoo}"));
    }

    @ThreadHostileTest("TestLoggingAdapter not thread-safe")
    @Test
    @UnAdaptableTest("W-2928878, we don't have test logging adapter in core")
    public void testParamLogging_MultipleParameters() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.components.test.java.controller.JavaTestController");
        Map<String, Object> params = Maps.newHashMap();
        params.put("we", "we");
        params.put("two", "two");

        ActionDef actionDef = controller.getSubDefinition("getMultiParamLogging");
        Action selectParamLoggingAction = instanceService.getInstance(actionDef, params);
        Set<String> logsSet = runActionsAndReturnLogs(Lists.newArrayList(selectParamLoggingAction));
        assertEquals(1, logsSet.size());
        assertTrue(
                "Failed to log a server action and multiple params",
                logsSet.contains(
                        "action_1$java://org.auraframework.components.test.java.controller.JavaTestController/ACTION$getMultiParamLogging{we,we}{two,two}"));
    }

    @ThreadHostileTest("TestLoggingAdapter not thread-safe")
    @Test
    @UnAdaptableTest("W-2928878, we don't have test logging adapter in core")
    public void testParamLogging_NullValuesForParameters() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.components.test.java.controller.JavaTestController");
        Map<String, Object> params = Maps.newHashMap();

        ActionDef actionDef = controller.getSubDefinition("getLoggableString");
        Action selectParamLoggingAction = instanceService.getInstance(actionDef, params);
        Set<String> logsSet = runActionsAndReturnLogs(Lists.newArrayList(selectParamLoggingAction));
        assertEquals(1, logsSet.size());
        assertTrue(
                "Failed to log a server action and param with null value",
                logsSet.contains(
                        "action_1$java://org.auraframework.components.test.java.controller.JavaTestController/ACTION$getLoggableString{param,null}"));
    }

    @ThreadHostileTest("TestLoggingAdapter not thread-safe")
    @Test
    @UnAdaptableTest("W-2928878, we don't have test logging adapter in core")
    public void testParamLogging_ParametersOfCustomDataType() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.components.test.java.controller.JavaTestController");
        Map<String, Object> params = Maps.newHashMap();
        params.put("param", new CustomParamType());

        ActionDef actionDef = controller.getSubDefinition("getCustomParamLogging");
        Action selectParamLoggingAction = instanceService.getInstance(actionDef, params);
        Set<String> logsSet = runActionsAndReturnLogs(Lists.newArrayList(selectParamLoggingAction));
        assertEquals(1, logsSet.size());
        assertTrue(
                "Logging custom action param time failed to call toString() of the custom type",
                logsSet.contains(
                        "action_1$java://org.auraframework.components.test.java.controller.JavaTestController/ACTION$getCustomParamLogging{param,CustomParamType_toString}"));
    }

    @ThreadHostileTest("TestLoggingAdapter not thread-safe")
    @Test
    @UnAdaptableTest("W-2928878, we don't have test logging adapter in core")
    public void testParamLogging_ChainingActions() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.impl.java.controller.ActionChainingController");
        Map<String, Object> params = Maps.newHashMap();
        params.put("a", 1);
        params.put("b", 1);
        params.put(
                "actions",
                "{\"actions\":[{\"descriptor\":\"java://org.auraframework.impl.java.controller.ActionChainingController/ACTION$multiply\",\"params\":{\"a\":2}}]}");

        ActionDef actionDef = controller.getSubDefinition("add");
        Action selectParamLoggingAction = instanceService.getInstance(actionDef, params);
        Set<String> logsSet = runActionsAndReturnLogs(Lists.newArrayList(selectParamLoggingAction));
        assertEquals(2, logsSet.size());
        assertTrue(
                "Failed to log server action",
                logsSet.contains(
                        "action_1$java://org.auraframework.impl.java.controller.ActionChainingController/ACTION$add"));
        assertTrue(
                "Failed to log chained server action",
                logsSet.contains(
                        "action_2$java://org.auraframework.impl.java.controller.ActionChainingController/ACTION$multiply"));
    }

    @ThreadHostileTest("TestLoggingAdapter not thread-safe")
    @Test
    @UnAdaptableTest("W-2928878, we don't have test logging adapter in core")
    public void testParamLogging_ChainingIdenticalActions() throws Exception {
        ControllerDef controller = getJavaController("java://org.auraframework.impl.java.controller.ActionChainingController");
        List<Action> actions = Lists.newArrayList();
        Map<String, Object> params = Maps.newHashMap();
        Action action;
        params.put("a", 1);
        params.put("b", 1);
        params.put(
                "actions",
                "{\"actions\":[{\"descriptor\":\"java://org.auraframework.impl.java.controller.ActionChainingController/ACTION$add\",\"params\":{\"a\":2, \"actions\":\"\"}}]}");

        ActionDef actionDef = controller.getSubDefinition("add");
        action = instanceService.getInstance(actionDef, params);
        actions.add(action);

        params = Maps.newHashMap();
        params.put("a", 1);
        params.put("b", 1);
        params.put("actions", null);
        action = instanceService.getInstance(actionDef, params);
        actions.add(action);

        Set<String> logsSet = runActionsAndReturnLogs(actions);
        assertEquals(3, logsSet.size());
        assertTrue(
                "Failed to log server action",
                logsSet.contains(
                        "action_1$java://org.auraframework.impl.java.controller.ActionChainingController/ACTION$add"));
        assertTrue(
                "Failed to log chained server action",
                logsSet.contains(
                        "action_2$java://org.auraframework.impl.java.controller.ActionChainingController/ACTION$add"));
        assertTrue(
                "Failed to log chained server action",
                logsSet.contains(
                        "action_3$java://org.auraframework.impl.java.controller.ActionChainingController/ACTION$add"));
    }

    /**
     * we run the list of actions, collect logs, parse them into a set of strings, return the set.
     * @param actions
     * @return
     * @throws Exception
     */
    private Set<String> runActionsAndReturnLogs(List<Action> actions) throws Exception {
    	boolean debug = false;
        List<LoggingEvent> logs;
        StringWriter sw = new StringWriter();
        appender.clearLogs();
        try {
            serverService.run(new Message(actions), contextService.getCurrentContext(), sw, null);
        } finally {
            loggingService.flush();
            logs = appender.getLog();
            assertNotNull(logs);
        }
        Set<String> logsSet = new HashSet<>();
        for(LoggingEvent le: logs) {
        	String message = le.getMessage().toString();
        	//we only care about log contain things like "action_1$java://org.auraframework.impl.java.controller.ActionChainingController/ACTION$bla;"
        	if(message.contains("action_1")) {
        		String[] msgList = message.split(";", 20);
        		for(String msg : msgList) {
        			if(msg.startsWith("action_")) {
        				//msg looks like this action_1$java://org.auraframework.impl.java.controller.ActionChainingController/ACTION$functionName{some parameter}: 4
        				//but we don't want the ': 4' at the end
        				logsSet.add(msg.substring(0, msg.lastIndexOf(':')));
            			if(debug) { System.out.println("add sub-msg:"+msg.substring(0, msg.lastIndexOf(':'))); }
        			} else {
        				if(debug) { System.out.println("ignore sub-msg:"+msg); }
        			}
        		}
        	} else {
        		if(debug) { System.out.println("ignore msg: "+message); }
        	}
        }
        return logsSet;
    }

    private static class TestLogger implements KeyValueLogger {

        private String key = null;
        private String value = null;

        @Override
        public void log(String lkey, String lvalue) {
            this.key = lkey;
            this.value = lvalue;
        }
    }
}
