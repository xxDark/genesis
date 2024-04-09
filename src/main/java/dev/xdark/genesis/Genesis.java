package dev.xdark.genesis;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public final class Genesis {
	private static final MethodHandles.Lookup LOOKUP;
	private static final PrivilegedReflection PRIVILEGED_REFLECTION;
	private static final MethodHandle CTOR_SLOT;
	private static final MethodHandle METHOD_CTOR;
	private static final MethodHandle ALLOCATE_INSTANCE;

	private Genesis() {
	}

	public static MethodHandle constructorAsInvoker(MethodHandle allocator) {
		MethodHandleInfo info = LOOKUP.revealDirect(allocator);
		MethodType mt = info.getMethodType();
		Class<?>[] parameterTypes = mt.parameterArray();
		Constructor<?> ctor;
		try {
			ctor = PRIVILEGED_REFLECTION.getGetDeclaredConstructor(info.getDeclaringClass(), parameterTypes);
		} catch (NoSuchMethodException ex) {
			throw new IllegalStateException("Cannot find constructor of %s with arguments %s".formatted(info.getDeclaringClass(), Arrays.toString(parameterTypes)), ex);
		}
		Method m = ctorToMethod(ctor);
		class InvokeHolder {
			static final MethodHandle INVOKE;

			static {
				try {
					INVOKE = LOOKUP.findVirtual(Method.class, "invoke",
							MethodType.methodType(Object.class, Object.class, Object[].class));
				} catch (NoSuchMethodException | IllegalAccessException ex) {
					throw new ExceptionInInitializerError(ex);
				}
			}
		}
		MethodType withInjectedReceiver = mt.insertParameterTypes(0, ctor.getDeclaringClass());

		return InvokeHolder.INVOKE.bindTo(m)
				.asCollector(1, Object[].class, mt.parameterCount())
				.asType(withInjectedReceiver);
	}

	public static <T> T allocateInstance(Class<? extends T> type) {
		try {
			//noinspection unchecked
			return (T) ALLOCATE_INSTANCE.invokeExact(type);
		} catch (Throwable t) {
			sneakyThrow(t);
			return null;
		}
	}

	public static MethodHandle makeAllocator(Class<?> type) {
		return ALLOCATE_INSTANCE.bindTo(type).asType(MethodType.methodType(type));
	}

	private static Method ctorToMethod(Constructor<?> ctor) {
		try {
			int slot = (int) CTOR_SLOT.invokeExact(ctor);
			return (Method) METHOD_CTOR.invokeExact(
					ctor.getDeclaringClass(),
					"init",
					ctor.getParameterTypes(),
					void.class,
					new Class[0],
					// This relies on the fact that (as of now...)
					// Java will make a call into the VM if method is native, and it will be dispatched
					// via JVM_InvokeMethod. That is what we want.
					Modifier.PUBLIC | Modifier.NATIVE,
					slot,
					(String) null,
					(byte[]) null,
					(byte[]) null,
					(byte[]) null
			);
		} catch (Throwable t) {
			sneakyThrow(t);
			throw new IllegalStateException(t);
		}
	}

	static {
		try {
			Field field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
			field.setAccessible(true);
			MethodHandles.publicLookup();
			MethodHandles.Lookup lookup = (MethodHandles.Lookup) field.get(null);
			PrivilegedReflection pr = PrivilegedReflection.instantiate();
			Field ctorSlot = pr.getDeclaredField(Constructor.class, "slot", int.class);
			MethodHandle methodConstructor = lookup.findConstructor(Method.class, MethodType.methodType(
					void.class,
					Class.class,
					String.class,
					Class[].class,
					Class.class,
					Class[].class,
					int.class,
					int.class,
					String.class,
					byte[].class,
					byte[].class,
					byte[].class
			));
			Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafe.setAccessible(true);
			MethodHandle allocateInstance = lookup.findVirtual(Unsafe.class, "allocateInstance", MethodType.methodType(Object.class, Class.class));
			allocateInstance = allocateInstance.bindTo(theUnsafe.get(null));
			CTOR_SLOT = lookup.unreflectGetter(ctorSlot);
			PRIVILEGED_REFLECTION = pr;
			LOOKUP = lookup;
			METHOD_CTOR = methodConstructor;
			ALLOCATE_INSTANCE = allocateInstance;
		} catch (ReflectiveOperationException ex) {
			throw new ExceptionInInitializerError(ex);
		}
	}

	private static <T extends Throwable> void sneakyThrow(Throwable t) throws T {
		//noinspection unchecked
		throw (T) t;
	}
}
