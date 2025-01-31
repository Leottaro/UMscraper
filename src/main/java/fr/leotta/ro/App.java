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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private static final Logger logger = LogManager.getLogger(App.class);

    public static WebDriver driver;
    public static WebDriverWait wait;

    public static String ent_login_email;
    public static String ent_password;
    public static Path geckodriver_path;
    public static String gmail_login_email;
    public static String gmail_password;
    public static String from_email;
    public static String to_email;
    public static Duration sleep_time;

    public static void main(String[] args) throws IOException {
        setDotEnvValues();

        logger.info("Starting the UMscraper");
        while (true) {
            webDriverInit();
            scrapNotes();
            try {
                logger.info(
                    "Sleeping {}",
                    sleep_time
                        .toString()
                        .substring(2)
                        .replaceAll("(\\d[HMS])(?!$)", "$1 ")
                        .toLowerCase()
                );
                Thread.sleep(sleep_time.toMillis());
            } catch (InterruptedException e) {
                logger.error("InterruptedException occurred", e);
                return;
            }
        }
    }

    public static void setDotEnvValues() throws IOException {
        logger.info("Setting up the environment variables");
        File dotenv_file = new File(".env");
        if (!dotenv_file.exists()) {
            logger.error(
                ".env file not found in the current directory, created one at {}",
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

        String raw_sleep_time = dotenv.get("SLEEP_TIME");
        Character unit = raw_sleep_time.charAt(raw_sleep_time.length() - 1);
        raw_sleep_time = raw_sleep_time.substring(0, raw_sleep_time.length() - 1);
        switch (unit) {
            case 's':
                sleep_time = Duration.ofSeconds(Long.parseLong(raw_sleep_time));
                break;
            case 'm':
                sleep_time = Duration.ofMinutes(Long.parseLong(raw_sleep_time));
                break;
            case 'h':
                sleep_time = Duration.ofHours(Long.parseLong(raw_sleep_time));
                break;
            default:
                logger.error("Invalid unit for SLEEP_TIME");
                throw new RuntimeException("Invalid unit for SLEEP_TIME");
        }

        // Check if the geckodriver exists
        if (!geckodriver_path.toFile().exists()) {
            logger.error(
                "Geckodriver not found at {}, please provide the correct path in the .env file. You can download the geckodriver at https://github.com/mozilla/geckodriver/releases/",
                geckodriver_path
            );
            throw new RuntimeException("Geckodriver not found");
        }

        // if from_email isn't specified, use the gmail_login_email as sender
        if (from_email == null || from_email.isEmpty()) {
            logger.warn("from_email not specified, using gmail_login_email as sender");
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
        logger.info("Initializing the WebDriver");
        System.setProperty("webdriver.gecko.driver", geckodriver_path.toString());
        FirefoxOptions options = new FirefoxOptions();
        options.addArguments("--headless");
        if (driver != null) {
            driver.close();
        }
        driver = new FirefoxDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(60));
    }

    public static boolean scrapNotes() {
        try {
            loginToEnt(driver, wait);
            ArrayList<ArrayList<String>> notes = fetchNotes(driver, wait);

            File notes_file = new File("notes.csv");
            if (!notes_file.exists()) {
                notes_file.createNewFile();
            }

            logger.info("Reading old notes");
            FileReader reader = new FileReader(notes_file);
            BufferedReader bufferedReader = new BufferedReader(reader);
            ArrayList<ArrayList<String>> old_notes = bufferedReader
                .lines()
                .map(line -> new ArrayList<>(Arrays.asList(line.split(";"))))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            bufferedReader.close();
            reader.close();

			if (notes.size() <= old_notes.size()) {
				logger.info("No new notes found");
				return true;
			}

            ArrayList<ArrayList<String>> notes_difference = new ArrayList<>();
            for (ArrayList<String> note : notes) {
                if (!old_notes.stream().anyMatch(old_note -> old_note.get(0).equals(note.get(0)))) {
                    notes_difference.add(note);
                }
            }

            logger.info("Writing new notes");
            FileWriter writer = new FileWriter(notes_file);
            writer.write(
                notes
                    .stream()
                    .map(note -> String.join(";", note))
                    .reduce("", (acc, note) -> acc + note + "\n")
            );
            writer.close();

            if (!notes_difference.isEmpty()) {
                logger.info("Changes detected ! sending email...");
				logger.info("notes found: {}", notes_difference);
				logger.info("old notes: {}", old_notes);
				logger.info("notes difference: {}", notes_difference);
                sendMail(
                    notes_difference
                        .stream()
                        .map(note -> String.join(" — ", note))
                        .reduce("", (acc, note) -> acc + note + "\n")
                );
            } else {
                logger.info("No changes detected");
            }
            return true;
        } catch (Exception e) {
            logger.error("Exception occurred while scraping notes", e);
            return false;
        }
    }

    private static void click(WebElement element) throws InterruptedException {
        waitFindElementRetry(ExpectedConditions.visibilityOf(element));
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        Thread.sleep(1000);
    }

    private static <T> T waitFindElementRetry(ExpectedCondition<T> expectedConditions)
        throws RuntimeException {
        int attempts = 1;
        while (attempts <= 3) {
            try {
                return wait.until(expectedConditions);
            } catch (StaleElementReferenceException e) {
                logger.warn("Element not found, retrying... ({}/3)", attempts);
            }
            attempts++;
        }
        throw new RuntimeException("Element not found");
    }

    private static void loginToEnt(WebDriver driver, WebDriverWait wait) {
        logger.info("Logging in to ENT");
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
        logger.info("Fetching notes");
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

            logger.info("Fetching notes for {}", filiereText);
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
            logger.info("Email sent successfully");
        } catch (MessagingException e) {
            logger.error("Error while sending the email", e);
        }
    }
}
