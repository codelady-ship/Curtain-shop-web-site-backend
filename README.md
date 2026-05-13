# Curtain-shop-web-site-backend
### **Overview**

This project is the backend for the leads management system, built using **Spring Boot** and **PostgreSQL**. It handles the core functionality of processing lead data, including adding new leads, updating statuses, managing promo codes, and storing relevant data.

### **Technologies**

* **Java 11** or higher
* **Spring Boot**
* **PostgreSQL**
* **JPA (Java Persistence API)**
* **MapStruct** for DTO mapping
* **CORS** for cross-origin requests
* **Spring Security** (if applicable for your project)
* **Lombok** for reducing boilerplate code

### **Setup**

1. **Clone the Repository**

   ```bash
   git clone https://github.com/username/repository.git
   ```

2. **Install Dependencies**
   In the backend directory, run the following command to install dependencies:

   ```bash
   ./gradle clean install
   ```

3. **Database Configuration**
   Make sure to update the `application.properties` or `application.yml` file with your database credentials:

   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/your_db_name
   spring.datasource.username=your_username
   spring.datasource.password=your_password
   ```

4. **Run the Application**
   You can run the backend using Maven:

   ```bash
   ./gradle spring-boot:run
   ```

   Alternatively, you can build the JAR file and run it:

   ```bash
   ./gradle clean package
   java -jar target/your-application.jar
   ```

   The backend will be available at `http://localhost:8080`.

### **Endpoints**

* **GET** `/api/leads` - Fetch leads with filtering options.
* **POST** `/api/leads/upload` - Upload visualization image for a lead.
* **POST** `/api/leads` - Create new leads.
* **GET** `/api/leads/{id}` - Get a single lead by ID.
* **PUT** `/api/leads/{id}/status` - Update lead status.
* **PUT** `/api/leads/{id}/contacted` - Update contacted status for a lead.
* **PUT** `/api/leads/{id}/promo` - Update promo code for a lead.

