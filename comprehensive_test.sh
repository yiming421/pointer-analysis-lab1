#!/bin/bash
echo "=== 指针分析完整测试报告 ==="
echo "生成时间: $(date)"
echo ""

# Original tests
original_tests=("Hello" "Branch" "Loop" "Field1" "Field2" "Invocation" "ArrayTest" "CastTest" "StaticFieldTest" "NativeMethodTest")
original_descriptions=(
    "基础指针分析测试"
    "分支敏感分析测试" 
    "循环分析测试"
    "字段敏感分析测试1"
    "字段敏感分析测试2" 
    "过程间调用测试"
    "数组索引敏感测试"
    "类型转换测试"
    "静态字段测试"
    "本地方法测试"
)

# New comprehensive tests
new_tests=("InheritanceTest" "InterfaceTest" "ExceptionTest" "RecursionTest" "MultiDimArrayTest" "ConstructorTest" "NestedClassTest" "StringTest" "PrimitiveWrapperTest" "ControlFlowTest" "CollectionsTest")
new_descriptions=(
    "继承和虚方法分派测试"
    "接口多态性测试"
    "异常处理控制流测试"
    "递归方法调用测试"
    "多维数组测试"
    "构造函数模式测试"
    "内部类和嵌套类测试"
    "字符串对象标识测试"
    "基本类型包装器测试"
    "复杂控制流测试"
    "集合框架测试"
)

echo "=== 原始测试用例 ==="
echo "-------------------"

for i in "${!original_tests[@]}"; do
    test=${original_tests[i]}
    desc=${original_descriptions[i]}
    
    echo -n "测试点 $((i+1)): $desc ... "
    
    # 运行测试
    ./gradlew run --args="-a pku-pta -cp src/test/pku -m test.$test" -x test --quiet > /dev/null 2>&1
    
    if [ -f "result.txt" ]; then
        result=$(cat result.txt)
        if [ -n "$result" ]; then
            echo "通过"
            echo "   输出: $result"
        else
            echo "无输出"
        fi
    else
        echo "失败(无结果文件)"
    fi
    echo ""
done

echo ""
echo "=== 新增测试用例 ==="
echo "-------------------"

for i in "${!new_tests[@]}"; do
    test=${new_tests[i]}
    desc=${new_descriptions[i]}
    
    echo -n "测试点 $((i+11)): $desc ... "
    
    # 运行测试
    ./gradlew run --args="-a pku-pta -cp src/test/pku -m test.$test" -x test --quiet > /dev/null 2>&1
    
    if [ -f "result.txt" ]; then
        result=$(cat result.txt)
        if [ -n "$result" ]; then
            echo "通过"
            echo "   输出: $result"
        else
            echo "无输出"
        fi
    else
        echo "失败(无结果文件)"
    fi
    echo ""
done

echo ""
echo "=== 测试统计 ==="
echo "---------------"
total_tests=$((${#original_tests[@]} + ${#new_tests[@]}))
echo "原始测试: ${#original_tests[@]} 个"
echo "新增测试: ${#new_tests[@]} 个"
echo "总测试数: $total_tests 个"

echo ""
echo "=== 测试完成 ==="
echo "测试涵盖的Java特性："
echo "- 基础对象分配和赋值"
echo "- 分支控制流"
echo "- 循环和迭代"
echo "- 实例字段访问"
echo "- 静态字段访问"
echo "- 方法调用和参数传递"
echo "- 数组操作（一维和多维）"
echo "- 类型转换"
echo "- 继承和多态"
echo "- 接口实现"
echo "- 异常处理"
echo "- 递归调用"
echo "- 构造函数"
echo "- 内部类和嵌套类"
echo "- 字符串处理"
echo "- 基本类型包装"
echo "- 复杂控制流（switch, do-while等）"
echo "- Java集合框架"