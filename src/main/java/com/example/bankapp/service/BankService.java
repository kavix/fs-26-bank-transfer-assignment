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
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // default for every method: read-only transaction
public class BankService {

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

        int updatedRows = customerRepository.debit(fromAccount, amount);
        if (updatedRows == 0) {
            throw new InsufficientBalanceException(fromAccount);
        }

        if (simulateFailure) {
            throw new RuntimeException("Simulated system failure after the withdrawal!");
        }

        customerRepository.credit(toAccount, amount);

        BankTransaction transaction = new BankTransaction();
        transaction.setFromAccount(fromAccount);
        transaction.setToAccount(toAccount);
        transaction.setAmount(amount);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setDescription("Transfer completed");
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
