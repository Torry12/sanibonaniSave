# 💰 Monetary Formatting - Quick Reference

**Updated**: June 3, 2026

---

## 🎯 Golden Rule
**Every monetary value displayed to users MUST have exactly 2 decimal places.**

---

## ⚡ Quick Usage

### Display Money (Most Common)
```kotlin
val amount = 1234.5
Text("Total: ${amount.formatZAR()}")  // "Total: R1,234.50" ✅
```

### Accept User Input
```kotlin
val userInput = "1234.56"
val amount = userInput.parseMoneyAmountOrNull()  // Validates & rounds ✅
```

### Calculate Money
```kotlin
val base = 1000.0.toMoneyBigDecimal()
val fee = base.multiply(BigDecimal("0.10"))
val result = fee.toDouble().roundMoneyToTwoDecimals()  // ✅
```

### Display Percentage
```kotlin
val percent = 15.5
Text("Rate: ${percent.formatPercent()}")  // "Rate: 15.50%" ✅
```

---

## 📋 Copy-Paste Patterns

### Pattern A: Display Amount
```kotlin
Text(amount.formatZAR())
```

### Pattern B: Display Multiple Amounts
```kotlin
Column {
    Text("Contribution: ${contribution.formatZAR()}")
    Text("Balance: ${balance.formatZAR()}")
    Text("Fee: ${fee.formatZAR()}")
}
```

### Pattern C: Display with Fallback
```kotlin
Text("Total: ${amount?.formatZARSafe() ?: "R0.00"}")
```

### Pattern D: User Input Field
```kotlin
TextField(
    value = amount.formatPlain(),  // "1234.56" (no symbol)
    onValueChange = { newValue ->
        val parsed = newValue.parseMoneyAmountOrNull()
        parsed?.let { updateAmount(it.toDouble()) }
    }
)
```

### Pattern E: Payment Summary
```kotlin
Card {
    Column(Modifier.padding(16.dp)) {
        Text("Amount", style = MaterialTheme.typography.labelMedium)
        Text(amount.formatZAR(), style = MaterialTheme.typography.headlineSmall)
        
        Text("Fee", style = MaterialTheme.typography.labelMedium)
        Text(fee.formatZAR(), style = MaterialTheme.typography.bodyMedium)
        
        Divider()
        
        Text("Total", style = MaterialTheme.typography.labelMedium)
        Text((amount + fee).formatZAR(), style = MaterialTheme.typography.headlineSmall)
    }
}
```

---

## ❌ DON'T DO THIS

```kotlin
// ❌ Wrong #1: No formatter
Text("Amount: $amount")  // "Amount: 1234.5"

// ❌ Wrong #2: toString()
Text("Amount: ${amount.toString()}")  // Loses precision

// ❌ Wrong #3: Manual format
Text("Amount: ${String.format("%.2f", amount)}")  // Works but non-standard

// ❌ Wrong #4: Calculating with formatted strings
val total = "${a.formatZAR()} + ${b.formatZAR()}".toDouble()  // Don't do this!

// ❌ Wrong #5: Storing without rounding
member.balance = userInput.toDouble()  // Could be 1234.5678

// ❌ Wrong #6: Null without safe version
val text = amount.formatZAR()  // Crashes if amount is null!
```

---

## ✅ DO THIS

```kotlin
// ✅ Right #1: Use extension
Text("Amount: ${amount.formatZAR()}")  // "Amount: R1,234.50"

// ✅ Right #2: Parse input
val parsed = input.parseMoneyAmountOrNull()

// ✅ Right #3: Calculate
val total = a.toMoneyBigDecimal().add(b.toMoneyBigDecimal())

// ✅ Right #4: Display calculated
Text("Total: ${total.toDouble().formatZAR()}")

// ✅ Right #5: Store rounded
member.balance = input.parseMoneyAmountOrNull()?.toDouble() ?: 0.0

// ✅ Right #6: Null safe
Text("Amount: ${amount?.formatZARSafe()}")
```

---

## 📚 Function Cheat Sheet

| Need | Function | Input | Output |
|------|----------|-------|--------|
| Display ZAR | `.formatZAR()` | `1234.5` | `"R1,234.50"` |
| Display plain | `.formatPlain()` | `1234.5` | `"1234.50"` |
| Display percent | `.formatPercent()` | `15.5` | `"15.50%"` |
| Display null-safe | `.formatZARSafe()` | `null` | `"R0.00"` |
| Parse input | `.parseMoneyAmountOrNull()` | `"1234.56"` | `BigDecimal` |
| Round number | `.roundMoneyToTwoDecimals()` | `1234.567` | `1234.57` |
| To BigDecimal | `.toMoneyBigDecimal()` | `1234.567` | `BigDecimal("1234.57")` |
| Compare | `MoneyFormatter.compareMoney()` | `a, b` | `-1, 0, or 1` |
| Check equal | `MoneyFormatter.areEqual()` | `a, b` | `Boolean` |

---

## 🚀 Import Statement

Add to files that use monetary formatting:

```kotlin
import com.sanibonani.save.domain.utils.formatZAR
import com.sanibonani.save.domain.utils.formatPlain
import com.sanibonani.save.domain.utils.formatPercent
import com.sanibonani.save.domain.utils.formatZARSafe
import com.sanibonani.save.domain.utils.formatPlainSafe
import com.sanibonani.save.domain.utils.parseMoneyAmountOrNull
import com.sanibonani.save.domain.utils.roundMoneyToTwoDecimals
import com.sanibonani.save.domain.utils.toMoneyBigDecimal
import com.sanibonani.save.domain.utils.MoneyFormatter
```

---

## 🧪 5-Minute Test

Try these in a screen and verify all show exactly 2 decimals:

```kotlin
@Composable
fun MoneyFormattingTest() {
    Column(Modifier.padding(16.dp)) {
        Text(100.0.formatZAR())
        Text(100.1.formatZAR())
        Text(100.12.formatZAR())
        Text(100.126.formatZAR())  // rounds to 100.13
        Text(0.0.formatZAR())
        Text(0.01.formatZAR())
        Text(0.99.formatZAR())
        Text(1234567.89.formatZAR())
    }
}
```

Expected output:
```
R100.00
R100.10
R100.12
R100.13  ← Note: rounded
R0.00
R0.01
R0.99
R1,234,567.89
```

---

## 🆘 Troubleshooting

**Q: Currency symbol not showing?**  
A: Use `.formatZAR()` not `.formatPlain()`

**Q: Too many decimals showing?**  
A: Ensure you're using formatters, not `toString()`

**Q: Null pointer exception?**  
A: Use `.formatZARSafe()` instead of `.formatZAR()`

**Q: Number won't parse?**  
A: Use `.parseMoneyAmountOrNull()` and check for null result

**Q: Calculations giving wrong results?**  
A: Use `BigDecimal` for math, not plain `Double`

---

**Need more details?** See `MONETARY_FORMATTING_STANDARD.md`

