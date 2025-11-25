# PowerShell script to run comprehensive pointer analysis tests
# Save as comprehensive_test.ps1

Write-Host "=== Pointer Analysis Comprehensive Test Report ===" -ForegroundColor Green
Write-Host "Generated at: $(Get-Date)" -ForegroundColor Gray
Write-Host ""

# Original tests
$originalTests = @("Hello", "Branch", "Loop", "Field1", "Field2", "Invocation", "ArrayTest", "CastTest", "StaticFieldTest", "NativeMethodTest")
$originalDescriptions = @(
    "Basic pointer analysis test",
    "Branch-sensitive analysis test",
    "Loop analysis test", 
    "Field-sensitive analysis test 1",
    "Field-sensitive analysis test 2",
    "Inter-procedural call test",
    "Array index-sensitive test",
    "Type casting test",
    "Static field test",
    "Native method test"
)

# New comprehensive tests
$newTests = @("InheritanceTest", "InterfaceTest", "ExceptionTest", "RecursionTest", "MultiDimArrayTest", "ConstructorTest", "NestedClassTest", "StringTest", "PrimitiveWrapperTest", "ControlFlowTest", "CollectionsTest")
$newDescriptions = @(
    "Inheritance and virtual method dispatch test",
    "Interface polymorphism test", 
    "Exception handling control flow test",
    "Recursive method call test",
    "Multi-dimensional array test",
    "Constructor pattern test",
    "Inner and nested class test",
    "String object identity test",
    "Primitive wrapper test",
    "Complex control flow test",
    "Collections framework test"
)

# Function to run a single test
function Run-Test {
    param($testName, $testNumber, $description)
    
    Write-Host "Test $testNumber`: $description ... " -NoNewline
    
    try {
        # Run the test
        $output = & .\gradlew run --args="-a pku-pta -cp src/test/pku -m test.$testName" -x test --quiet 2>&1
        
        # Check if result file exists
        if (Test-Path "result.txt") {
            $result = Get-Content "result.txt" -Raw -ErrorAction SilentlyContinue
            if ($result -and $result.Trim()) {
                # Check if the result contains meaningful data (not just empty colons)
                $lines = $result.Trim() -split "`n"
                $hasValidResults = $false
                
                foreach ($line in $lines) {
                    # Look for lines like "1 : 100" or "2 : 200" with actual allocation IDs
                    if ($line -match '^\s*\d+\s*:\s*\d+') {
                        $hasValidResults = $true
                        break
                    }
                }
                
                if ($hasValidResults) {
                    Write-Host "PASS" -ForegroundColor Green
                    Write-Host "   Output: $($result.Trim())" -ForegroundColor White
                    return "PASS"
                } else {
                    Write-Host "FAIL (empty results)" -ForegroundColor Red
                    Write-Host "   Output: $($result.Trim())" -ForegroundColor Gray
                    return "FAIL"
                }
            } else {
                Write-Host "FAIL (no output)" -ForegroundColor Red
                return "FAIL"
            }
        } else {
            Write-Host "FAIL (no result file)" -ForegroundColor Red
            return "FAIL"
        }
    }
    catch {
        Write-Host "ERROR: $_" -ForegroundColor Red
        return "ERROR"
    }
    Write-Host ""
}

Write-Host "=== Original Test Cases ===" -ForegroundColor Yellow
Write-Host "----------------------------" -ForegroundColor Yellow

$passCount = 0
$failCount = 0

for ($i = 0; $i -lt $originalTests.Length; $i++) {
    $result = Run-Test -testName $originalTests[$i] -testNumber ($i + 1) -description $originalDescriptions[$i]
    if ($result -eq "PASS") { $passCount++ } else { $failCount++ }
}

Write-Host ""
Write-Host "=== New Comprehensive Test Cases ===" -ForegroundColor Cyan
Write-Host "-----------------------------------" -ForegroundColor Cyan

for ($i = 0; $i -lt $newTests.Length; $i++) {
    $result = Run-Test -testName $newTests[$i] -testNumber ($i + $originalTests.Length + 1) -description $newDescriptions[$i]
    if ($result -eq "PASS") { $passCount++ } else { $failCount++ }
}

# Summary
$totalTests = $originalTests.Length + $newTests.Length
Write-Host ""
Write-Host "=== Test Summary ===" -ForegroundColor Magenta
Write-Host "--------------------" -ForegroundColor Magenta
Write-Host "Original tests: $($originalTests.Length)" -ForegroundColor White
Write-Host "New tests: $($newTests.Length)" -ForegroundColor White
Write-Host "Total tests: $totalTests" -ForegroundColor White
Write-Host "Passed: $passCount" -ForegroundColor Green
Write-Host "Failed: $failCount" -ForegroundColor Red
Write-Host "Success rate: $([math]::Round($passCount / $totalTests * 100, 1))%" -ForegroundColor $(if ($passCount -eq $totalTests) { "Green" } else { "Yellow" })

Write-Host ""
Write-Host "=== Java Features Covered ===" -ForegroundColor Green
Write-Host "- Basic object allocation and assignment"
Write-Host "- Branch control flow"
Write-Host "- Loops and iteration"
Write-Host "- Instance field access"
Write-Host "- Static field access"
Write-Host "- Method calls and parameter passing"
Write-Host "- Array operations (1D and multi-dimensional)"
Write-Host "- Type casting"
Write-Host "- Inheritance and polymorphism"
Write-Host "- Interface implementation"
Write-Host "- Exception handling"
Write-Host "- Recursive calls"
Write-Host "- Constructors"
Write-Host "- Inner and nested classes"
Write-Host "- String processing"
Write-Host "- Primitive type boxing/unboxing"
Write-Host "- Complex control flow (switch, do-while, etc.)"
Write-Host "- Java Collections Framework"
Write-Host ""
Write-Host "=== Test Complete ===" -ForegroundColor Green