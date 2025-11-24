#!/bin/bash
echo "=== 指针分析测试报告 ==="
echo "生成时间: $(date)"
echo ""

tests=("Hello" "Branch" "Loop" "Field1" "Field2" "Invocation")
descriptions=(
    "基础指针分析测试"
    "分支敏感分析测试" 
    "循环分析测试"
    "字段敏感分析测试1"
    "字段敏感分析测试2" 
    "过程间调用测试"
)

echo "测试进度:"
echo "---------"

for i in "${!tests[@]}"; do
    test=${tests[i]}
    desc=${descriptions[i]}
    
    echo -n "测试点 $((i+1)): $desc ... "
    
    # 运行测试
    ./gradlew run --args="-a pku-pta -cp src/test/pku -m test.$test" -x test --quiet > /dev/null 2>&1
    
    if [ -f "result.txt" ]; then
        result=$(cat result.txt)
        if [ -n "$result" ]; then
            echo "   输出: $result"
        else
            echo "无输出"
        fi
    else
        echo "失败(无结果文件)"
    fi
done

echo ""
echo "=== 测试完成 ==="
