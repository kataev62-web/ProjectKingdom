package ru.projectkingdom.enterprise;

public enum EnterpriseBindingRole {
    CASHBOX("Касса"), RAW_STORAGE("Склад сырья"), FINISHED_STORAGE("Склад продукции");
    private final String title;
    EnterpriseBindingRole(String title){this.title=title;}
    public String title(){return title;}
}
