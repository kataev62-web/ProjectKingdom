package ru.projectkingdom.enterprise;

public enum EnterpriseBindingRole {
    CASHBOX("Касса"), RAW_STORAGE("Склад сырья"), FINISHED_STORAGE("Склад продукции"), SALARY_STORAGE("Зарплатный сейф");
    private final String title;
    EnterpriseBindingRole(String title){this.title=title;}
    public String title(){return title;}
}
