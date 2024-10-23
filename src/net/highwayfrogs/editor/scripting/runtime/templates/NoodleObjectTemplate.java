package net.highwayfrogs.editor.scripting.runtime.templates;

import lombok.Getter;
import net.highwayfrogs.editor.scripting.NoodleScriptEngine;
import net.highwayfrogs.editor.scripting.compiler.NoodleCallHolder;
import net.highwayfrogs.editor.scripting.runtime.*;
import net.highwayfrogs.editor.scripting.runtime.templates.NoodleTemplateFunction.ConstructorTemplateFunction;
import net.highwayfrogs.editor.scripting.runtime.templates.NoodleTemplateFunction.LazyNoodleTemplateFunction;
import net.highwayfrogs.editor.scripting.runtime.templates.NoodleTemplateFunction.NoodleStaticTemplateFunction;
import net.highwayfrogs.editor.utils.Consumer3;
import net.highwayfrogs.editor.utils.Function3;
import net.highwayfrogs.editor.utils.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.logging.Logger;

/**
 * An object template is effectively Noodle's concept of a class.
 * It defines all functions, fields, etc for a particular object type.
 */
public abstract class NoodleObjectTemplate<TType> {
    @Getter private final NoodleScriptEngine engine;
    @Getter private final String name;
    @Getter private final Class<TType> wrappedClass;
    private final Map<String, BiFunction<NoodleThread<?>, TType, NoodlePrimitive>> getters = new HashMap<>();
    private final Map<String, Consumer3<NoodleThread<?>, TType, NoodlePrimitive>> setters = new HashMap<>();
    private final NoodleCallHolder<NoodleTemplateFunction<TType, ?>> instanceFunctions = new NoodleCallHolder<>();
    private final NoodleCallHolder<NoodleStaticTemplateFunction<TType, ?>> staticFunctions = new NoodleCallHolder<>();

    public static final String CONSTRUCTOR_FUNCTION_NAME = "new";

    public NoodleObjectTemplate(NoodleScriptEngine engine, Class<TType> wrappedClass, String name) {
        this.engine = engine;
        this.name = name;
        this.wrappedClass = wrappedClass;
    }
    
    private Logger getLogger() {
        return this.engine.getLogger();
    }
    
    private void warn(String template, Object... args) {
        getLogger().warning(Utils.formatStringSafely(template, args));
    }

    private void info(String template, Object... args) {
        getLogger().info(Utils.formatStringSafely(template, args));
    }

    /**
     * Sets up the template.
     */
    public void setup() {
        this.onSetup();
        this.instanceFunctions.registerCallable(new NoodleTemplateEqualsFunction<>(this, NoodleThread.class));
        addGetter("template", (thread, value) -> thread.getStack().pushString(getName()));
    }

    /**
     * Sets up the template.
     */
    protected abstract void onSetup();

    /**
     * Called when a thread shuts down, regardless of what the shutdown cause was.
     * @param thread The thread which shutdown.
     * @param value The heap object impacted by the shutdown.
     * @param instance The object instance impacted by the shutdown.
     */
    public void onThreadShutdown(NoodleThread<?> thread, TType value, NoodleObjectInstance instance) {
        // Do nothing.
    }

    /**
     * Test if an object is supported by this template.
     * @param object The object to test.
     * @return If the object is supported.
     */
    public final boolean isObjectSupported(Object object) {
        return object != null && this.wrappedClass.isInstance(object) && this.isSupported(this.wrappedClass.cast(object));
    }

    /**
     * Test if an object is supported by this template.
     * @param object The object to test.
     * @return If the object is supported.
     */
    protected boolean isSupported(TType object) {
        return true;
    }

    /**
     * Test if the contents of two TType values are equal.
     * @param value1 The first value to test.
     * @param value2 The second value to test.
     * @return areContentsEqual?
     */
    public boolean areObjectContentsEqual(TType value1, TType value2) {
        return Objects.equals(value1, value2);
    }

    /**
     * Called when an object is added to a thread's heap.
     * This method is intended to be overriden so that any objects which have references tracked to other objects can register those objects when the main object is registered.
     * @param thread The thread the object has been added to.
     * @param object The object which has been added to the heap.
     */
    public void onObjectAddToHeap(NoodleThread<?> thread, TType object, NoodleObjectInstance instance) {
        // Do nothing by default.
    }

    /**
     * Called when an object is freed from its threads heap.
     * This method is intended to be overriden so that any objects which have references tracked to other objects can release them when they are freed.
     * @param thread The thread the object has been freed from.
     * @param object The object which has been freed.
     */
    public void onObjectFree(NoodleThread<?> thread, TType object, NoodleObjectInstance instance) {
        // Do nothing by default.
    }

    /**
     * Registers a field getter.
     * @param fieldName The name of the field.
     * @param getter The getter logic.
     */
    public void addGetter(String fieldName, BiFunction<NoodleThread<?>, TType, NoodlePrimitive> getter) {
        Object existingGetter = this.getters.put(fieldName, getter);
        if (existingGetter != null) {
            warn("NoodleObjectTemplate-%s.%s already had a getter! It has been replaced.", this.name, fieldName);
            Utils.printStackTrace();
        }
    }

    /**
     * Registers a field setter.
     * @param fieldName The name of the field.
     * @param setter The setter logic.
     */
    public void addSetter(String fieldName, Consumer3<NoodleThread<?>, TType, NoodlePrimitive> setter) {
        Object existingSetter = this.setters.put(fieldName, setter);
        if (existingSetter != null) {
            warn("NoodleObjectTemplate-%s.%s already had a setter! It has been replaced.", this.name, fieldName);
            Utils.printStackTrace();
        }
    }

    /**
     * Registers a field getter.
     * @param fieldName The name of the field.
     * @param getter The getter logic.
     * @param setter The setter logic.
     */
    public void addGetterAndSetter(String fieldName, BiFunction<NoodleThread<?>, TType, NoodlePrimitive> getter, Consumer3<NoodleThread<?>, TType, NoodlePrimitive> setter) {
        addGetter(fieldName, getter);
        addSetter(fieldName, setter);
    }

    /**
     * Registers a function in this template.
     * @param function The function to register.
     */
    public <TThread extends NoodleThread<?>> void addFunction(NoodleTemplateFunction<TType, TThread> function) {
        if (CONSTRUCTOR_FUNCTION_NAME.equalsIgnoreCase(function.getName()) && !(function instanceof ConstructorTemplateFunction)) {
            Utils.printStackTrace();
            warn("Tried to register a function with a constructor name for Noodle %s!", this.name);
            return;
        }

        if (!this.instanceFunctions.registerCallable(function)) {
            Utils.printStackTrace();
            warn("NoodleObjectTemplate-%s.%s(%d args) is already registered.", this.name, function.getName(), function.getArgumentCount());
        }
    }

    /**
     * Registers a function in this template.
     * @param name The name of the function.
     * @param threadClass The required thread type.
     * @param delegateHandler Function code.
     * @param argumentNames Names of required arguments
     */
    public <TThread extends NoodleThread<?>> void addFunction(String name, Class<TThread> threadClass, Function3<TThread, TType, NoodlePrimitive[], NoodlePrimitive> delegateHandler, String... argumentNames) {
        addFunction(new LazyNoodleTemplateFunction<>(name, this.wrappedClass, threadClass, delegateHandler, argumentNames));
    }

    /**
     * Registers a function in this template.
     * @param name The name of the function.
     * @param delegateHandler Function code.
     * @param argumentNames Names of required arguments
     */
    @SuppressWarnings({"rawtypes", "unchecked"})

    public void addFunction(String name, Function3<NoodleThread<?>, TType, NoodlePrimitive[], NoodlePrimitive> delegateHandler, String... argumentNames) {
        addFunction(new LazyNoodleTemplateFunction(name, this.wrappedClass, NoodleThread.class, delegateHandler, argumentNames));
    }

    /**
     * Registers a static function in this template.
     * @param name The name of the function.
     * @param delegateHandler Function code.
     * @param argumentNames Names of required arguments
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addStaticFunction(String name, BiFunction<NoodleThread<?>, NoodlePrimitive[], NoodlePrimitive> delegateHandler, String... argumentNames) {
        addStaticFunction(new NoodleStaticTemplateFunction(name, this.wrappedClass, NoodleThread.class, delegateHandler, argumentNames));
    }

    /**
     * Registers a static function in this template.
     * @param name The name of the function.
     * @param threadClass The required thread type.
     * @param delegateHandler Function code.
     * @param argumentNames Names of required arguments
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <TThread extends NoodleThread<?>> void addStaticFunction(String name, Class<TThread> threadClass, BiFunction<NoodleThread<?>, NoodlePrimitive[], NoodlePrimitive> delegateHandler, String... argumentNames) {
        addStaticFunction(new NoodleStaticTemplateFunction(name, this.wrappedClass, threadClass, delegateHandler, argumentNames));
    }

    /**
     * Registers a static function.
     * @param function The function to register.
     */
    public <TThread extends NoodleThread<?>> void addStaticFunction(NoodleStaticTemplateFunction<TType, TThread> function) {
        if (!this.staticFunctions.registerCallable(function)) {
            Utils.printStackTrace();
            warn("NoodleObjectTemplate-STATIC/%s.%s(%d args) is already registered.", this.name, function.getName(), function.getArgumentCount());
        }
    }

    /**
     * Registers a constructor.
     * @param constructor The constructor code.
     * @param argumentNames The names of the constructor arguments.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addConstructor(BiFunction<NoodleThread<?>, NoodlePrimitive[], TType> constructor, String... argumentNames) {
        addStaticFunction(new ConstructorTemplateFunction(this.wrappedClass, NoodleThread.class, constructor, argumentNames));
    }

    /**
     * Registers a constructor.
     * @param threadClass The class of the thread which the constructor can be used with.
     * @param constructor The constructor code.
     * @param argumentNames The names of the constructor arguments.
     * @param <TThread> The thread type which the constructor can be used with.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <TThread extends NoodleThread<?>> void addConstructor(Class<TThread> threadClass, BiFunction<NoodleThread<?>, NoodlePrimitive[], TType> constructor, String... argumentNames) {
        addStaticFunction(new ConstructorTemplateFunction(this.wrappedClass, threadClass, constructor, argumentNames));
    }
    
    /**
     * Execute an instance function.
     * @param instance The object instance to execute the function under.
     * @param thread The thread to execute the function under.
     * @param functionName The name of the function to execute.
     * @param arguments The arguments to send to the function.
     * @exception NoodleRuntimeException Thrown if an issue occurs attempting to run the instance function.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public NoodlePrimitive executeFunction(TType instance, NoodleThread<?> thread, String functionName, NoodlePrimitive[] arguments) {
        int argumentCount = (arguments != null ? arguments.length : 0);
        NoodleTemplateFunction function = this.getInstanceFunction(functionName, argumentCount);
        if (function == null)
            throw new NoodleRuntimeException("The template for object '%s' does not have an instance function called %s taking %d arguments.", getName(), functionName, argumentCount);

        int stackSize = thread.getStack().size();
        NoodlePrimitive resultValue;
        try {
            resultValue = function.execute(thread, instance, arguments);
        } catch (Throwable ex) {
            StringBuilder builder = new StringBuilder("Error executing Noodle template function: '");
            function.writeSignature(builder);
            builder.append("' with arguments (");
            writeArguments(builder, arguments);
            builder.append(").");

            throw new NoodleRuntimeException(ex, builder.toString());
        }

        // If the thread hasn't been paused or destroyed, add the result to the stack.
        if (stackSize == thread.getStack().size() && thread.getStatus() == NoodleThreadStatus.RUNNING) {
            resultValue = thread.getStack().pushPrimitive(resultValue);
            warn("Function %s.%s didn't push a return value on the stack.", getName(), function.getSignature());
        }

        return resultValue;
    }


    /**
     * Execute an instance function.
     * @param thread The thread to execute the function under.
     * @param functionName The name of the function to execute.
     * @param arguments The arguments to send to the function.
     * @exception NoodleRuntimeException Thrown if an issue occurs attempting to run the instance function.
     */
    public static <TType> NoodlePrimitive executeInstanceFunction(TType instance, NoodleThread<?> thread, String functionName, NoodlePrimitive[] arguments) {
        // Find instance function.
        NoodleObjectTemplate<TType> objTemplate = thread.getHeap().getObjectTemplate(instance);
        if (objTemplate == null)
            throw new NoodleRuntimeException("The object '%s' does not have a Noodle template. Somehow, Noodle tried to call %s on it.", Utils.getSimpleName(instance), functionName);

        return objTemplate.executeFunction(instance, thread, functionName, arguments);
    }

    /**
     * Prints a list of all functions valid for this template.
     */
    public void printFunctionList() {
        this.staticFunctions.forEach(func ->
                info("  - Static %s.%s: ", getName(), func.getSignature()));
        this.instanceFunctions.forEach(func ->
                info("  - Instanced %s.%s: ", getName(), func.getSignature()));
        this.getters.keySet().forEach(fieldName ->
                info("  - Getter for %s.%s: ", getName(), fieldName));
        this.setters.keySet().forEach(fieldName ->
                info("  - Setter for %s.%s: ", getName(), fieldName));
    }

    private static void writeArguments(StringBuilder builder, NoodlePrimitive[] arguments) {
        for (int i = 0; i < arguments.length; i++) {
            if (i > 0)
                builder.append(", ");
            builder.append(arguments[i]);
        }
    }

    /**
     * Looks up an instance function with a given name and argument count.
     * @param functionName The name of the function to lookup.
     * @param argumentCount The number of arguments the function uses.
     * @return foundFunction
     */
    public NoodleTemplateFunction<?, ?> getInstanceFunction(String functionName, int argumentCount) {
        return this.instanceFunctions.getByNameAndArgumentCount(functionName, argumentCount);
    }

    /**
     * Looks up a static function with a given name and argument count.
     * @param functionName The name of the function to lookup.
     * @param argumentCount The number of arguments the function uses.
     * @return foundFunction
     */
    public NoodleStaticTemplateFunction<?, ?> getStaticFunction(String functionName, int argumentCount) {
        return this.staticFunctions.getByNameAndArgumentCount(functionName, argumentCount);
    }

    /**
     * Gets the getter function for a given field name.
     * @param fieldName The name of the field to lookup.
     * @return getter, or null if one does not exist.
     */
    public BiFunction<NoodleThread<?>, TType, NoodlePrimitive> getGetter(String fieldName) {
        return this.getters.get(fieldName);
    }

    /**
     * Gets the setter function for a given field name.
     * @param fieldName The name of the field to lookup.
     * @return setter, or null if one does not exist.
     */
    public Consumer3<NoodleThread<?>, TType, NoodlePrimitive> getSetter(String fieldName) {
        return this.setters.get(fieldName);
    }

    /**
     * Gets the object instance corresponding with the given template.
     * Throws an error if the value is null.
     * @param template The template to get the object with.
     * @param <TObject> The type of the object expected.
     * @return objectInstance
     */
    protected static <TObject> TObject getRequiredObjectInstance(NoodleObjectTemplate<TObject> template, NoodlePrimitive[] args, int argumentIndex) {
        if (argumentIndex < 0)
            throw new NoodleRuntimeException("Cannot get NoodlePrimitive from argument index %d.", argumentIndex);
        if (argumentIndex >= args.length)
            throw new NoodleRuntimeException("Cannot get NoodlePrimitive from argument index %d, because there are only %d arguments.", args.length);

        NoodlePrimitive primitive = args[argumentIndex];
        if (!primitive.isObjectReference())
            throw new NoodleRuntimeException("Cannot use NoodlePrimitive [%s] as a(n) %s object.", primitive, template.getName());

        TObject objectInstance = primitive.getObjectInstance(template);
        if (objectInstance == null)
            throw new NoodleRuntimeException("Expected a(n) %s Noodle object at argument index %d, but it was null.", template.getName(), argumentIndex);

        return objectInstance;
    }

    /**
     * Gets the object instance corresponding with the given template.
     * Throws an error if the value is null.
     * @param template The template to get the object with.
     * @param <TObject> The type of the object expected.
     * @return objectInstance
     */
    protected static <TObject> TObject getRequiredObjectInstance(NoodleObjectTemplate<TObject> template, NoodlePrimitive primitive, String fieldName) {
        if (!primitive.isObjectReference())
            throw new NoodleRuntimeException("Cannot use NoodlePrimitive [%s] as a(n) %s object.", primitive, template.getName());

        TObject objectInstance = primitive.getObjectInstance(template);
        if (objectInstance == null)
            throw new NoodleRuntimeException("Cannot set '%s.%s' to be null.", template.getName(), fieldName);

        return objectInstance;
    }

    /**
     * Gets the noodle thread as a certain type, or throws an exception if it isn't that type.
     * @param threadClass The type of thread to get.
     * @param thread The thread to cast.
     * @return castedThread
     * @param <TThread> The type of thread to get back.
     */
    public static <TThread> TThread getRequiredThread(Class<TThread> threadClass, NoodleThread<?> thread) {
        if (!threadClass.isInstance(thread))
            throw new NoodleRuntimeException("This instanced function call requires a %s thread, but got %s instead.", Utils.getSimpleName(threadClass), Utils.getSimpleName(thread));
        return threadClass.cast(thread);
    }

    private class NoodleTemplateEqualsFunction<TThread extends NoodleThread<?>> extends NoodleTemplateFunction<TType, TThread> {
        private final NoodleObjectTemplate<TType> template;

        public NoodleTemplateEqualsFunction(NoodleObjectTemplate<TType> template, Class<TThread> threadClass) {
            super("equals", template.getWrappedClass(), threadClass, "other");
            this.template = template;
        }

        @Override
        protected NoodlePrimitive executeImpl(TThread thread, TType thisRef, NoodlePrimitive[] args) {
            NoodleObjectInstance otherRef = args[0].getObjectReference();
            if (otherRef == null)
                return thread.getStack().pushBoolean(false);

            TType other = otherRef.getOptionalObjectInstance(getWrappedClass());
            return thread.getStack().pushBoolean(this.template.areObjectContentsEqual(thisRef, other));
        }
    }
}