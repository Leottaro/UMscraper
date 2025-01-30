package fr.leotta.ro;

import io.github.cdimascio.dotenv.Dotenv;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class App {

    public static WebDriver driver;
    public static WebDriverWait wait;

    public static String ent_login_email;
    public static String ent_password;
    public static Path geckodriver_path;
    public static String gmail_login_email;
    public static String gmail_password;
    public static String from_email;
    public static String to_email;

    public static void main(String[] args) throws IOException {
        setDotEnvValues();
        webDriverInit();

        System.out.println("Starting the UMscraper");
        while (scrapNotes()) {
            try {
                System.out.println("Sleeping for 1 hour");
                Thread.sleep(Duration.ofMinutes(60).toMillis());
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    public static void setDotEnvValues() throws IOException {
        System.out.println("Setting up the environment variables");
        File dotenv_file = new File(".env");
        if (!dotenv_file.exists()) {
            System.err.format(
                ".env file not found in the current directory, created one at %s\n",
                dotenv_file.getAbsolutePath()
            );
            FileWriter writer = new FileWriter(dotenv_file);
            writer.write(
                "ENT_LOGIN_EMAIL=\"\"\nENT_PASSWORD=\"\"\nNOTES_PATH=\"\"\nGECKODRIVER_PATH=\"\"\nGMAIL_LOGIN_EMAIL=\"\"\nGMAIL_PASSWORD=\"\"\nFROM_EMAIL=\"\"\nTO_EMAIL=\"\""
            );
            writer.close();
            throw new RuntimeException("Please fill the .env file with the required information");
        }

        Dotenv dotenv = Dotenv.configure().load();
        ent_login_email = dotenv.get("ENT_LOGIN_EMAIL");
        ent_password = dotenv.get("ENT_PASSWORD");
        geckodriver_path =
            Paths
                .get(
                    dotenv
                        .get("GECKODRIVER_PATH")
                        .replaceFirst("^~", System.getProperty("user.home"))
                )
                .toAbsolutePath()
                .normalize();
        gmail_login_email = dotenv.get("GMAIL_LOGIN_EMAIL");
        gmail_password = dotenv.get("GMAIL_PASSWORD");
        from_email = dotenv.get("FROM_EMAIL");
        to_email = dotenv.get("TO_EMAIL");

        // Check if the geckodriver exists
        if (!geckodriver_path.toFile().exists()) {
            System.err.format(
                "Geckodriver not found at %s, please provide the correct path in the .env file. You can download the geckodriver at https://github.com/mozilla/geckodriver/releases/\n",
                geckodriver_path
            );
            throw new RuntimeException("Geckodriver not found");
        }

        // if from_email isn't specified, use the gmail_login_email as sender
        if (from_email == null || from_email.isEmpty()) {
            System.out.println("from_email not specified, using gmail_login_email as sender");
            from_email = "" + gmail_login_email;
        }

        if (
            ent_login_email.isEmpty() ||
            ent_password.isEmpty() ||
            geckodriver_path.toString().isEmpty() ||
            gmail_login_email.isEmpty() ||
            gmail_password.isEmpty() ||
            from_email.isEmpty() ||
            to_email.isEmpty()
        ) {
            throw new RuntimeException("Please fill the .env file with the required information");
        }
    }

    public static void webDriverInit() {
        System.out.println("Initializing the WebDriver");
        System.setProperty("webdriver.gecko.driver", geckodriver_path.toString());
        FirefoxOptions options = new FirefoxOptions();
        options.addArguments("--headless");
        driver = new FirefoxDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(60));
    }

    public static boolean scrapNotes() {
        try {
            loginToEnt(driver, wait);
            ArrayList<ArrayList<String>> notes = fetchNotes(driver, wait);
            String notesString = notes
                .stream()
                .map(note -> String.join(";", note))
                .reduce("", (acc, note) -> acc + note + "\n");
            int nb_notes = notes.size() + 1; // +1 because the first row should be the header

            System.out.println("Writing notes to file");
            File notes_file = new File("notes.csv");
            if (!notes_file.exists()) {
                notes_file.createNewFile();
            }

            System.out.println("Reading old notes");
            FileReader reader = new FileReader(notes_file);
            BufferedReader bufferedReader = new BufferedReader(reader);
            int old_nb_notes = 0;
            while (bufferedReader.readLine() != null) {
                old_nb_notes++;
            }
            bufferedReader.close();
            reader.close();

            System.out.println("Writing new notes");
            FileWriter writer = new FileWriter(notes_file);
            writer.write("Code;Libellé;Session 1\n");
            writer.write(notesString);
            writer.close();

            if (old_nb_notes != nb_notes) {
                System.out.println("Changes detected ! sending email...");
                sendMail(notesString);
            } else {
                System.out.println("No changes detected");
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            driver.quit();
        }
    }

    private static void click(WebElement element) throws InterruptedException {
        waitFindElementRetry(ExpectedConditions.visibilityOf(element));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        Thread.sleep(1000);
    }

    private static <T> T waitFindElementRetry(ExpectedCondition<T> expectedConditions)
        throws RuntimeException {
        int attempts = 0;
        while (attempts < 3) {
            try {
                return wait.until(expectedConditions);
            } catch (StaleElementReferenceException e) {
                System.out.println("element not found, retrying... (" + attempts + ")");
            }
            attempts++;
        }
        throw new RuntimeException("Element not found");
    }

    private static void loginToEnt(WebDriver driver, WebDriverWait wait) {
        System.out.println("Logging in to ENT");
        driver.get("https://ent.umontpellier.fr");
        WebElement usernameField = waitFindElementRetry(
            ExpectedConditions.visibilityOfElementLocated(By.id("username"))
        );
        WebElement passwordField = waitFindElementRetry(
            ExpectedConditions.visibilityOfElementLocated(By.id("password"))
        );
        usernameField.sendKeys(ent_login_email);
        passwordField.sendKeys(ent_password);
        WebElement loginButton = waitFindElementRetry(
            ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[type=submit]"))
        );
        loginButton.click();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
    }

    private static ArrayList<ArrayList<String>> fetchNotes(WebDriver driver, WebDriverWait wait)
        throws InterruptedException {
        System.out.println("Fetching notes");
        driver.get("https://app.umontpellier.fr/mdw/#!notesView");

        ArrayList<ArrayList<String>> notes = new ArrayList<>();

        WebElement tableauFilieres = waitFindElementRetry(
            ExpectedConditions.visibilityOfAllElementsLocatedBy(
                By.cssSelector(
                    "[class=\"v-table v-widget noscrollabletable v-table-noscrollabletable v-has-width\"]"
                )
            )
        )
            .get(1);
        int nbFilieres = tableauFilieres
            .findElements(
                By.cssSelector(
                    "[class=\"v-button v-widget link v-button-link v-link v-button-v-link\"]"
                )
            )
            .size();

        for (int i = 1; i < nbFilieres; i += 2) {
            tableauFilieres =
                waitFindElementRetry(
                    ExpectedConditions.visibilityOfAllElementsLocatedBy(
                        By.cssSelector(
                            "[class=\"v-table v-widget noscrollabletable v-table-noscrollabletable v-has-width\"]"
                        )
                    )
                )
                    .get(1);
            WebElement filiere = tableauFilieres
                .findElements(
                    By.cssSelector(
                        "[class=\"v-button v-widget link v-button-link v-link v-button-v-link\"]"
                    )
                )
                .get(i);
            String filiereText = filiere.getText();

            click(filiere);
            WebElement divPopup = waitFindElementRetry(
                ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("[class=\"v-window v-widget v-has-width v-has-height\"]")
                )
            );

            System.out.println("Fetching notes for " + filiereText);
            List<WebElement> rows = divPopup.findElements(By.tagName("tr"));
            for (WebElement row : rows) {
                List<WebElement> cols = row.findElements(By.tagName("td"));
                String code = cols.get(0).getText().trim();
                String description = cols.get(1).getText().trim();
                String session1 = cols.get(2).getText().split("/")[0].trim();
                if (code.isEmpty() || description.isEmpty() || session1.isEmpty()) {
                    continue;
                }
                if (code.equals("Code")) {
                    notes.add(new ArrayList<>(Arrays.asList(filiereText)));
                } else {
                    notes.add(new ArrayList<>(Arrays.asList(code, description, session1)));
                }
            }

            WebElement closeButton = divPopup.findElement(
                By.cssSelector("[class=\"v-window-closebox\"]")
            );
            click(closeButton);
        }

        return notes;
    }

    public static void sendMail(String content) {
        // Set up properties for the mail session
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        // Create a session with an authenticator
        Session session = Session.getInstance(
            props,
            new javax.mail.Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(gmail_login_email, gmail_password);
                }
            }
        );

        try {
            // Create a new email message
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from_email));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to_email));
            message.setSubject("UMscraper Notification");
            message.setText(content);

            // Send the email
            Transport.send(message);
            System.out.println("Email sent successfully");
        } catch (MessagingException e) {
            System.err.println("Error while sending the email\n" + e.getMessage());
        }
    }
}
