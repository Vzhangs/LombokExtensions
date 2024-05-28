## Extensions of project lombok

### How to use
1. clone project lombok
2. copy src.java.lombok file to lombok.src.core
3. rebuild lombok using `ant dist`
4. import new package in package management system

### Annotations
`@Nullable`

Example Data Class
```java
public class ExampleDataClass {
    public StringDataElement stringData;
    public intDataElement intData;
}
public class StringDataElement {
    public String dataName;
    public String dataValue;
}
public class intDataElement {
    public String dataName;
    public int dataValue;
}
```
With Lombok Extensions

```java
import lombok.Nullable;

public class ExampleClass {

    public void getDataValue(ExampleDataClass input) {
        @Nullable
        int intDataValue = input.intData.dataValue;
        @Nullable
        String stringDataValue = input.stringData.dataValue;
    }

}
```
Vanilla Java
```java
public class ExampleClass {

    public void getDataValue(ExampleDataClass input) {
        
        int intDataValue;
        try {
            intDataValue = input.intData.dataValue;
        } catch (NullPointerException e) {
            intDataValue = 0;
        }
        
        String stringDataValue;
        try {
            stringDataValue = input.stringData.dataValue;
        } catch (NullPointerException e) {
            stringDataValue = null;
        }
    }

}
```
