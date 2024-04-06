package dev.xdark.genesis;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

final class PrivilegedReflection {
	private final Method getDeclaredFields;
	private final Method getDeclaredMethods;
	private final Method getDeclaredConstructors;

	private PrivilegedReflection(Method getDeclaredFields, Method getDeclaredMethods, Method getDeclaredConstructors) {
		this.getDeclaredFields = getDeclaredFields;
		this.getDeclaredMethods = getDeclaredMethods;
		this.getDeclaredConstructors = getDeclaredConstructors;
	}

	public Field getDeclaredField(Class<?> holder, String name, Class<?> type) throws ReflectiveOperationException {
		for (Field f : (Field[]) getDeclared(getDeclaredFields, holder)) {
			if (!name.equals(f.getName())) continue;
			if (!type.equals(f.getType())) continue;
			return f;
		}
		throw new NoSuchFieldException("%s of type %s in %s".formatted(name, type, holder));
	}

	public Method getDeclaredMethod(Class<?> holder, String name, Class<?>... parameterTypes) throws ReflectiveOperationException {
		for (Method m : (Method[]) getDeclared(getDeclaredMethods, holder)) {
			if (!name.equals(m.getName())) continue;
			if (!Arrays.equals(parameterTypes, m.getParameterTypes())) continue;
			return m;
		}
		throw new NoSuchMethodException("%s with parameters %s in %s".formatted(name, List.of(parameterTypes), holder));
	}

	public <T> Constructor<T> getGetDeclaredConstructor(Class<?> holder, Class<?>... parameterTypes) throws NoSuchMethodException {
		for (Constructor<?> c : (Constructor<?>[]) getDeclared(getDeclaredConstructors, holder)) {
			if (!Arrays.equals(parameterTypes, c.getParameterTypes())) continue;
			//noinspection unchecked
			return (Constructor<T>) c;
		}
		throw new NoSuchMethodException("Constructor with parameters %s in %s".formatted(List.of(parameterTypes), holder));
	}

	private static <T> T[] getDeclared(Method m, Class<?> holder) {
		try {
			//noinspection unchecked
			return (T[]) m.invoke(holder, false);
		} catch (InvocationTargetException e) {
			Throwable thrown = e.getTargetException();
			if (thrown instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException(thrown);
		} catch (IllegalAccessException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static PrivilegedReflection instantiate() {
		return new PrivilegedReflection(
				getDeclaredAccessor("Field"),
				getDeclaredAccessor("Method"),
				getDeclaredAccessor("Constructor")
		);
	}

	private static Method getDeclaredAccessor(String type) {
		try {
			Method method = Class.class.getDeclaredMethod("getDeclared%ss0".formatted(type), boolean.class);
			method.setAccessible(true);
			return method;
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException("No access to internal logic", ex);
		}
	}

}
