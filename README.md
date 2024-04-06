# genesis

Control when constructor is called.

```java
public class Test {

	public Test() {
		System.out.println(this);
	}

	public static void main(String[] args) throws Throwable {
		Test test = Genesis.allocateInstance(Test.class);
		MethodHandle ctor = MethodHandles.publicLookup().findConstructor(Test.class, MethodType.methodType(void.class));
		MethodHandle constructorInvoker = Genesis.construtorAsInvoker(ctor);
		constructorInvoker.invokeExact(test);
		constructorInvoker.invokeExact(test);
	}
}
```