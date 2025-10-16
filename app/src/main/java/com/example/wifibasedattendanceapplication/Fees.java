package com.example.wifibasedattendanceapplication;

import java.util.List;

public class Fees {
    private String academicYear;
    private int totalFees;
    private int paidFees;
    private int remainingFees;
    private FeeStructure feeStructure;
    private List<PaymentHistory> paymentHistory;
    private List<DueDate> dueDates;
    private int lateFee;
    private Scholarship scholarship;
    private String feeStatus;
    private String lastPaymentDate;
    private String nextDueDate;

    // Default constructor
    public Fees() {}

    // Constructor with all parameters
    public Fees(String academicYear, int totalFees, int paidFees, int remainingFees,
                FeeStructure feeStructure, List<PaymentHistory> paymentHistory,
                List<DueDate> dueDates, int lateFee, Scholarship scholarship,
                String feeStatus, String lastPaymentDate, String nextDueDate) {
        this.academicYear = academicYear;
        this.totalFees = totalFees;
        this.paidFees = paidFees;
        this.remainingFees = remainingFees;
        this.feeStructure = feeStructure;
        this.paymentHistory = paymentHistory;
        this.dueDates = dueDates;
        this.lateFee = lateFee;
        this.scholarship = scholarship;
        this.feeStatus = feeStatus;
        this.lastPaymentDate = lastPaymentDate;
        this.nextDueDate = nextDueDate;
    }

    // Getters and Setters
    public String getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(String academicYear) {
        this.academicYear = academicYear;
    }

    public int getTotalFees() {
        return totalFees;
    }

    public void setTotalFees(int totalFees) {
        this.totalFees = totalFees;
    }

    public int getPaidFees() {
        return paidFees;
    }

    public void setPaidFees(int paidFees) {
        this.paidFees = paidFees;
    }

    public int getRemainingFees() {
        return remainingFees;
    }

    public void setRemainingFees(int remainingFees) {
        this.remainingFees = remainingFees;
    }

    public FeeStructure getFeeStructure() {
        return feeStructure;
    }

    public void setFeeStructure(FeeStructure feeStructure) {
        this.feeStructure = feeStructure;
    }

    public List<PaymentHistory> getPaymentHistory() {
        return paymentHistory;
    }

    public void setPaymentHistory(List<PaymentHistory> paymentHistory) {
        this.paymentHistory = paymentHistory;
    }

    public List<DueDate> getDueDates() {
        return dueDates;
    }

    public void setDueDates(List<DueDate> dueDates) {
        this.dueDates = dueDates;
    }

    public int getLateFee() {
        return lateFee;
    }

    public void setLateFee(int lateFee) {
        this.lateFee = lateFee;
    }

    public Scholarship getScholarship() {
        return scholarship;
    }

    public void setScholarship(Scholarship scholarship) {
        this.scholarship = scholarship;
    }

    public String getFeeStatus() {
        return feeStatus;
    }

    public void setFeeStatus(String feeStatus) {
        this.feeStatus = feeStatus;
    }

    public String getLastPaymentDate() {
        return lastPaymentDate;
    }

    public void setLastPaymentDate(String lastPaymentDate) {
        this.lastPaymentDate = lastPaymentDate;
    }

    public String getNextDueDate() {
        return nextDueDate;
    }

    public void setNextDueDate(String nextDueDate) {
        this.nextDueDate = nextDueDate;
    }

    // Inner class for Fee Structure
    public static class FeeStructure {
        private int tuitionFee;
        private int libraryFee;
        private int laboratoryFee;
        private int examinationFee;
        private int developmentFee;
        private int sportsFee;
        private int transportFee;

        public FeeStructure() {}

        public FeeStructure(int tuitionFee, int libraryFee, int laboratoryFee,
                           int examinationFee, int developmentFee, int sportsFee, int transportFee) {
            this.tuitionFee = tuitionFee;
            this.libraryFee = libraryFee;
            this.laboratoryFee = laboratoryFee;
            this.examinationFee = examinationFee;
            this.developmentFee = developmentFee;
            this.sportsFee = sportsFee;
            this.transportFee = transportFee;
        }

        // Getters and Setters
        public int getTuitionFee() { return tuitionFee; }
        public void setTuitionFee(int tuitionFee) { this.tuitionFee = tuitionFee; }

        public int getLibraryFee() { return libraryFee; }
        public void setLibraryFee(int libraryFee) { this.libraryFee = libraryFee; }

        public int getLaboratoryFee() { return laboratoryFee; }
        public void setLaboratoryFee(int laboratoryFee) { this.laboratoryFee = laboratoryFee; }

        public int getExaminationFee() { return examinationFee; }
        public void setExaminationFee(int examinationFee) { this.examinationFee = examinationFee; }

        public int getDevelopmentFee() { return developmentFee; }
        public void setDevelopmentFee(int developmentFee) { this.developmentFee = developmentFee; }

        public int getSportsFee() { return sportsFee; }
        public void setSportsFee(int sportsFee) { this.sportsFee = sportsFee; }

        public int getTransportFee() { return transportFee; }
        public void setTransportFee(int transportFee) { this.transportFee = transportFee; }
    }

    // Inner class for Payment History
    public static class PaymentHistory {
        private String paymentId;
        private int amount;
        private String paymentDate;
        private String paymentMethod;
        private String transactionId;
        private String status;
        private String description;

        public PaymentHistory() {}

        public PaymentHistory(String paymentId, int amount, String paymentDate,
                             String paymentMethod, String transactionId, String status, String description) {
            this.paymentId = paymentId;
            this.amount = amount;
            this.paymentDate = paymentDate;
            this.paymentMethod = paymentMethod;
            this.transactionId = transactionId;
            this.status = status;
            this.description = description;
        }

        // Getters and Setters
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }

        public String getPaymentDate() { return paymentDate; }
        public void setPaymentDate(String paymentDate) { this.paymentDate = paymentDate; }

        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    // Inner class for Due Dates
    public static class DueDate {
        private int installment;
        private int amount;
        private String dueDate;
        private String status;
        private String description;

        public DueDate() {}

        public DueDate(int installment, int amount, String dueDate, String status, String description) {
            this.installment = installment;
            this.amount = amount;
            this.dueDate = dueDate;
            this.status = status;
            this.description = description;
        }

        // Getters and Setters
        public int getInstallment() { return installment; }
        public void setInstallment(int installment) { this.installment = installment; }

        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }

        public String getDueDate() { return dueDate; }
        public void setDueDate(String dueDate) { this.dueDate = dueDate; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    // Inner class for Scholarship
    public static class Scholarship {
        private int amount;
        private String type;
        private String status;
        private String description;

        public Scholarship() {}

        public Scholarship(int amount, String type, String status, String description) {
            this.amount = amount;
            this.type = type;
            this.status = status;
            this.description = description;
        }

        // Getters and Setters
        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
