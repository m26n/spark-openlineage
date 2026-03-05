CREATE TABLE public.customers (
    customer_id BIGINT PRIMARY KEY,
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    email TEXT NOT NULL,
    dob DATE NOT NULL,
    customer_since DATE NOT NULL,
    city TEXT NOT NULL,
    country TEXT NOT NULL
);

INSERT INTO public.customers (
    customer_id,
    first_name,
    last_name,
    email,
    dob,
    customer_since,
    city,
    country
) VALUES
    (1001, 'Ada', 'Lovelace', 'ada@example.com', DATE '1990-12-10', DATE '2018-05-14', 'London', 'UK'),
    (1002, 'Grace', 'Hopper', 'grace@example.com', DATE '1985-07-22', DATE '2016-09-01', 'New York', 'USA'),
    (1003, 'Linus', 'Torvalds', 'linus@example.com', DATE '1994-03-03', DATE '2021-01-19', 'Helsinki', 'Finland'),
    (1004, 'Katherine', 'Johnson', 'katherine@example.com', DATE '1979-08-26', DATE '2012-11-07', 'Hampton', 'USA'),
    (1005, 'Margaret', 'Hamilton', 'margaret@example.com', DATE '1999-01-15', DATE '2023-06-30', 'Cambridge', 'USA');

