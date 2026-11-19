import java.sql.*;
import java.util.Scanner;

public class OrderManagementSystem {
    private static final String URL = "jdbc:mysql://localhost:3306/ecommerce_db";
    private static final String USER = "root";
    private static final String PASSWORD = "Enter_your_own_password"; // Replace with your MySQL root password

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to ecommerce_db successfully!");

            while (true) {
                System.out.println("\n=== E-COMMERCE ORDER MANAGEMENT SYSTEM ===");
                System.out.println("1. Insert Order Records (Min 3)");
                System.out.println("2. Search Orders by Status & Qty > 2 (Sorted by Unit Price DESC)");
                System.out.println("3. Add Quantity to Existing Order");
                System.out.println("4. Update Order Status (Pending / Shipped / Delivered)");
                System.out.println("5. Display Total Order Value & Delete Cancelled Order");
                System.out.println("6. Exit");
                System.out.print("Enter choice: ");

                int choice = scanner.nextInt();
                scanner.nextLine(); // Consume newline

                switch (choice) {
                    case 1 -> insertOrders(conn, scanner);
                    case 2 -> retrieveAndSortOrders(conn, scanner);
                    case 3 -> addOrderQuantity(conn, scanner);
                    case 4 -> updateOrderStatus(conn, scanner);
                    case 5 -> totalValueAndDeleteCancelled(conn, scanner);
                    case 6 -> {
                        System.out.println("Exiting application...");
                        return;
                    }
                    default -> System.out.println("Invalid choice. Please try again.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Database connection failed! Check your URL, username, password, or MySQL service.");
            e.printStackTrace();
        }
    }

    // (a) Insert at least 3 order records using PreparedStatement & handle duplicate order_id
    private static void insertOrders(Connection conn, Scanner scanner) {
        String sql = "INSERT INTO Orders (order_id, customer_name, product_name, quantity, unit_price, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int count = 0;
            while (count < 3) {
                System.out.println("\nEnter details for Order " + (count + 1) + " of 3:");
                System.out.print("Order ID: ");
                int id = scanner.nextInt();
                scanner.nextLine();

                System.out.print("Customer Name: ");
                String customerName = scanner.nextLine();

                System.out.print("Product Name: ");
                String productName = scanner.nextLine();

                System.out.print("Quantity: ");
                int quantity = scanner.nextInt();

                System.out.print("Unit Price: ");
                double unitPrice = scanner.nextDouble();
                scanner.nextLine();

                System.out.print("Status (Pending / Shipped / Delivered / Cancelled): ");
                String status = scanner.nextLine();

                pstmt.setInt(1, id);
                pstmt.setString(2, customerName);
                pstmt.setString(3, productName);
                pstmt.setInt(4, quantity);
                pstmt.setDouble(5, unitPrice);
                pstmt.setString(6, status);

                try {
                    pstmt.executeUpdate();
                    System.out.println("Order inserted successfully!");
                    count++;
                } catch (SQLIntegrityConstraintViolationException e) {
                    System.out.println("Error: Order ID " + id + " already exists. Please enter a unique Order ID.");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // (b) Display orders with given status and quantity > 2, sorted by unit_price DESC
    private static void retrieveAndSortOrders(Connection conn, Scanner scanner) {
        System.out.print("\nEnter order status: ");
        String status = scanner.nextLine();

        String sql = "SELECT * FROM Orders WHERE LOWER(status) = LOWER(?) AND quantity > 2 ORDER BY unit_price DESC";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status.trim());
            ResultSet rs = pstmt.executeQuery();

            System.out.println("\n--- Matching Orders (Status: " + status + ", Quantity > 2, Unit Price DESC) ---");
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.printf("Order ID: %d | Customer: %s | Product: %s | Qty: %d | Unit Price: %.2f | Status: %s%n",
                        rs.getInt("order_id"),
                        rs.getString("customer_name"),
                        rs.getString("product_name"),
                        rs.getInt("quantity"),
                        rs.getDouble("unit_price"),
                        rs.getString("status"));
            }
            if (!found) {
                System.out.println("No orders found matching status '" + status + "' with quantity greater than 2.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // (c) Increase existing quantity by given additional quantity if order exists
    private static void addOrderQuantity(Connection conn, Scanner scanner) {
        System.out.print("\nEnter Order ID: ");
        int id = scanner.nextInt();

        System.out.print("Enter additional quantity to add: ");
        int addQty = scanner.nextInt();
        scanner.nextLine();

        if (addQty <= 0) {
            System.out.println("Error: Additional quantity must be greater than 0.");
            return;
        }

        String sql = "UPDATE Orders SET quantity = quantity + ? WHERE order_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, addQty);
            pstmt.setInt(2, id);

            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                System.out.println("Order quantity increased by " + addQty + " successfully!");
            } else {
                System.out.println("Error: Order with ID " + id + " does not exist.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // (d) Update status only if order exists and new status is Pending, Shipped, or Delivered
    private static void updateOrderStatus(Connection conn, Scanner scanner) {
        System.out.print("\nEnter Order ID: ");
        int id = scanner.nextInt();
        scanner.nextLine();

        System.out.print("Enter new status (Pending, Shipped, Delivered): ");
        String newStatus = scanner.nextLine().trim();

        String normalizedStatus = switch (newStatus.toLowerCase()) {
            case "pending" -> "Pending";
            case "shipped" -> "Shipped";
            case "delivered" -> "Delivered";
            default -> null;
        };

        if (normalizedStatus == null) {
            System.out.println("Error: Invalid status '" + newStatus + "'. Allowed statuses are: Pending, Shipped, Delivered.");
            return;
        }

        String selectSql = "SELECT * FROM Orders WHERE order_id = ?";
        String updateSql = "UPDATE Orders SET status = ? WHERE order_id = ?";

        try (PreparedStatement selectStmt = conn.prepareStatement(selectSql);
             PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {

            selectStmt.setInt(1, id);
            ResultSet rs = selectStmt.executeQuery();

            if (!rs.next()) {
                System.out.println("Error: Order with ID " + id + " does not exist.");
                return;
            }

            updateStmt.setString(1, normalizedStatus);
            updateStmt.setInt(2, id);
            updateStmt.executeUpdate();

            // Fetch and display updated order details
            ResultSet updatedRs = selectStmt.executeQuery();
            if (updatedRs.next()) {
                System.out.println("\nOrder status updated successfully! Updated Details:");
                System.out.printf("Order ID: %d | Customer: %s | Product: %s | Qty: %d | Unit Price: %.2f | Status: %s%n",
                        updatedRs.getInt("order_id"),
                        updatedRs.getString("customer_name"),
                        updatedRs.getString("product_name"),
                        updatedRs.getInt("quantity"),
                        updatedRs.getDouble("unit_price"),
                        updatedRs.getString("status"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // (e) Display total order value using SUM(quantity * unit_price) & delete only if status is Cancelled
    private static void totalValueAndDeleteCancelled(Connection conn, Scanner scanner) {
        String aggSql = "SELECT COALESCE(SUM(quantity * unit_price), 0) AS total_order_value FROM Orders";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(aggSql)) {
            if (rs.next()) {
                double totalValue = rs.getDouble("total_order_value");
                System.out.printf("%nTotal Order Value: %.2f%n", totalValue);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.print("Enter Order ID to delete: ");
        int id = scanner.nextInt();
        scanner.nextLine();

        String checkSql = "SELECT status FROM Orders WHERE order_id = ?";
        String deleteSql = "DELETE FROM Orders WHERE order_id = ? AND LOWER(status) = 'cancelled'";

        try (PreparedStatement checkStmt = conn.prepareStatement(checkSql);
             PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {

            checkStmt.setInt(1, id);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) {
                System.out.println("Cannot delete: Order ID " + id + " does not exist.");
            } else {
                String currentStatus = rs.getString("status");
                if (!"Cancelled".equalsIgnoreCase(currentStatus)) {
                    System.out.println("Cannot delete: Order ID " + id + " has status '" + currentStatus + "'. Only 'Cancelled' orders can be deleted.");
                } else {
                    deleteStmt.setInt(1, id);
                    int deleted = deleteStmt.executeUpdate();
                    if (deleted > 0) {
                        System.out.println("Order ID " + id + " (Cancelled) deleted successfully.");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}