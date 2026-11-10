package com.example.bankapp.service;

import com.example.bankapp.exception.AccountNotFoundException;
import com.example.bankapp.exception.InsufficientBalanceException;
import com.example.bankapp.model.BankTransaction;
import com.example.bankapp.model.Customer;
import com.example.bankapp.model.TransactionStatus;
import com.example.bankapp.repository.BankTransactionRepository;
import com.example.bankapp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // default for every method: read-only transaction
public class BankService {

    public static final String BANK_FEE_ACCOUNT = "999999";

    private final CustomerRepository customerRepository;
    private final BankTransactionRepository bankTransactionRepository;

    @Transactional(rollbackFor = Exception.class)
    public BankTransaction transfer(String fromAccount, String toAccount, BigDecimal amount, boolean simulateFailure) {
        if (fromAccount == null || toAccount == null || fromAccount.trim().isEmpty() || toAccount.trim().isEmpty()) {
            throw new IllegalArgumentException("Account numbers cannot be empty.");
        }

        if (fromAccount.equals(toAccount)) {
            throw new IllegalArgumentException("Sender and receiver accounts must be different.");
        }

        Customer sender = customerRepository.getByAccountNumber(fromAccount)
                .orElseThrow(() -> new AccountNotFoundException(fromAccount));

        Customer receiver = customerRepository.getByAccountNumber(toAccount)
                .orElseThrow(() -> new AccountNotFoundException(toAccount));

        // 1% Transfer Fee calculation (Task 12)
        BigDecimal fee = amount.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDebit = amount.add(fee);

        int updatedRows = customerRepository.debit(fromAccount, totalDebit);
        if (updatedRows == 0) {
            throw new InsufficientBalanceException(fromAccount);
        }

        if (simulateFailure) {
            throw new RuntimeException("Simulated system failure after the withdrawal!");
        }

        // Credit receiver
        customerRepository.credit(toAccount, amount);

        // Credit bank fee account if fee > 0
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            customerRepository.getByAccountNumber(BANK_FEE_ACCOUNT)
                    .ifPresent(bankAcc -> customerRepository.credit(BANK_FEE_ACCOUNT, fee));
        }

        LocalDateTime now = LocalDateTime.now();

        // Record main transfer transaction
        BankTransaction transaction = new BankTransaction();
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setDescription(fee.compareTo(BigDecimal.ZERO) > 0
                ? "Transfer completed (Fee: LKR " + fee + ")"
                : "Transfer completed");
        transaction.setCreatedAt(now);
        BankTransaction savedTx = bankTransactionRepository.save(transaction);

        // Record fee transaction if fee > 0
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            BankTransaction feeTx = new BankTransaction();
            feeTx.setFromAccount(fromAccount);
            feeTx.setToAccount(BANK_FEE_ACCOUNT);
            feeTx.setAmount(fee);
            feeTx.setStatus(TransactionStatus.SUCCESS);
            feeTx.setDescription("Transfer fee (1%) for transfer to " + toAccount);
            feeTx.setCreatedAt(now);
            bankTransactionRepository.save(feeTx);
        }

        return savedTx;
    }

    @Transactional(rollbackFor = Exception.class)
    public BankTransaction deposit(String accountNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be greater than zero.");
        }

        Customer customer = customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        customerRepository.credit(accountNumber, amount);

        BankTransaction transaction = new BankTransaction();
        transaction.setFromAccount(null);
        transaction.setToAccount(accountNumber);
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setDescription("Deposit to account " + accountNumber);
        transaction.setCreatedAt(LocalDateTime.now());

        return bankTransactionRepository.save(transaction);
    }

    @Transactional(rollbackFor = Exception.class)
    public BankTransaction withdraw(String accountNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than zero.");
        }

        Customer customer = customerRepository.getByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        int updatedRows = customerRepository.debit(accountNumber, amount);
        if (updatedRows == 0) {
            throw new InsufficientBalanceException(accountNumber);
        }

        BankTransaction transaction = new BankTransaction();
        transaction.setFromAccount(accountNumber);
        transaction.setToAccount(null);
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setDescription("Withdrawal from account " + accountNumber);
        transaction.setCreatedAt(LocalDateTime.now());

        return bankTransactionRepository.save(transaction);
    }

    @Transactional
    public Customer createCustomer(Customer customer) {
        if (customerRepository.countAccounts(customer.getAccountNumber()) > 0) {
            throw new IllegalArgumentException("Account number already exists: " + customer.getAccountNumber());
        }
        if (customerRepository.countEmails(customer.getEmail()) > 0) {
            throw new IllegalArgumentException("Email already exists: " + customer.getEmail());
        }
        return customerRepository.save(customer);
    }
}
