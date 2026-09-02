package vn.personalfinance.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import vn.personalfinance.domain.model.*
import vn.personalfinance.domain.repository.ManualTransactionInput
import vn.personalfinance.presentation.screen.TransactionFormContent
import vn.personalfinance.presentation.theme.FinanceTheme

class TransactionFormTest {
 @get:Rule val compose=createComposeRule()
 @Test fun addTransactionFlow(){var saved:ManualTransactionInput?=null
  compose.setContent{FinanceTheme{TransactionFormContent(listOf(FinancialAccount("a","Ví tiền mặt","cash",0)),listOf(Category("c","Ăn uống",TransactionType.EXPENSE)),onSave={saved=it})}}
  compose.onNodeWithTag("amount_input").performTextInput("150000")
  compose.onNodeWithText("Tài khoản: Chọn ▾").performClick();compose.onNodeWithText("Ví tiền mặt").performClick()
  compose.onNodeWithText("Danh mục: Chọn ▾").performClick();compose.onNodeWithText("Ăn uống").performClick()
  compose.onNodeWithTag("description_input").performTextInput("Bữa trưa")
  compose.onNodeWithTag("save_transaction").performClick()
  compose.runOnIdle{assertEquals(150000,saved?.amount);assertEquals("a",saved?.accountId);assertEquals("c",saved?.categoryId)}
 }
}
