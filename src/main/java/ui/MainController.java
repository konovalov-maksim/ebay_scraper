package ui;

import core.*;
import core.entities.Release;
import core.entities.Result;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class MainController implements Initializable, Logger, ItemsSeeker.ResultsLoadingListener, UpcConvertor.ConvertorListener {

    @FXML private TextArea queriesTa;
    @FXML private TextArea upcTa;
    @FXML private TextArea fullTitleTa;
    @FXML private TextArea consoleTa;
    @FXML private Button searchingBtn;
    @FXML private Button stopBtn;
    @FXML private Button clearBtn;
    @FXML private ComboBox<String> conditionCb;
    @FXML private Spinner<Integer> maxThreadsSpn;
    @FXML private TextField itemsLimitTf;
    @FXML private TextField categoryNameTf;
    @FXML private TextField categoryIdTf;
    @FXML private ComboBox<String> categoryCb;
    @FXML private Button subcategoryBtn;
    @FXML private Button parentCategoryBtn;
    @FXML private Button convertBtn;



    @FXML private TableView<Result> table;
    @FXML private TableColumn<Result, String> queryCol;
    @FXML private TableColumn<Result, String> statusCol;
    @FXML private TableColumn<Result, Integer> activeItemsFoundCol;
    @FXML private TableColumn<Result, Integer> completeItemsTotalCol;
    @FXML private TableColumn<Result, Integer> completeItemsFoundCol;
    @FXML private TableColumn<Result, Integer> soldItemsCol;
    @FXML private TableColumn<Result, Double> avgPriceListedCol;
    @FXML private TableColumn<Result, Double> avgPriceSoldCol;
    @FXML private TableColumn<Result, String> soldRatioCol;
    @FXML private TableColumn<Result, Double> curValueCol;
    private TableContextMenu tableContextMenu;

    private ObservableList<Result> results = FXCollections.observableArrayList();
    private Set<String> resultsSet = new HashSet<>();
    private List<String> notFoundUpcs = new ArrayList<>();


    private ItemsSeeker itemsSeeker;
    private UpcConvertor convertor;
    private String appName;
    private String discogsToken;
    private Category category;


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        try {
            appName = Files.readAllLines(Paths.get("key.txt")).get(0);
            discogsToken = Files.readAllLines(Paths.get("discogs_token.txt")).get(0);
        } catch (IOException e) {
            log("Unable to read token");
        }
        Category.setAppName(appName);
        selectCategory("-1");

        queryCol.setCellValueFactory(new PropertyValueFactory<>("query"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("statusString"));
        activeItemsFoundCol.setCellValueFactory(new PropertyValueFactory<>("activeItemsFound"));
        completeItemsTotalCol.setCellValueFactory(new PropertyValueFactory<>("completeItemsTotal"));
        completeItemsFoundCol.setCellValueFactory(new PropertyValueFactory<>("completeItemsFound"));
        soldItemsCol.setCellValueFactory(new PropertyValueFactory<>("soldItems"));
        avgPriceListedCol.setCellValueFactory(new PropertyValueFactory<>("avgPriceListed"));
        avgPriceSoldCol.setCellValueFactory(new PropertyValueFactory<>("avgPriceSold"));
        soldRatioCol.setCellValueFactory(new PropertyValueFactory<>("soldRatioString"));
        curValueCol.setCellValueFactory(new PropertyValueFactory<>("curValue"));

        queryCol.prefWidthProperty().bind(table.widthProperty().multiply(0.3));
        statusCol.prefWidthProperty().bind(table.widthProperty().multiply(0.1));
        activeItemsFoundCol.prefWidthProperty().bind(table.widthProperty().multiply(0.1));
        soldItemsCol.prefWidthProperty().bind(table.widthProperty().multiply(0.1));
        avgPriceListedCol.prefWidthProperty().bind(table.widthProperty().multiply(0.1));
        avgPriceSoldCol.prefWidthProperty().bind(table.widthProperty().multiply(0.1));
        soldRatioCol.prefWidthProperty().bind(table.widthProperty().multiply(0.1));
        curValueCol.prefWidthProperty().bind(table.widthProperty().multiply(0.1));
        soldRatioCol.setComparator((o1, o2) -> {
                    int first = (int) Math.round(Double.parseDouble(o1.replaceAll("%", "")) * 10);
                    int second = (int) Math.round(Double.parseDouble(o2.replaceAll("%", "")) * 10);
                    return  first - second;
        });
        table.setItems(results);
        tableContextMenu = new TableContextMenu(table);
        tableContextMenu.getActiveUrlItem().setOnAction(a ->
                openUrl(table.getSelectionModel().getSelectedItem().getSearchUrlActive()));
        tableContextMenu.getSoldUrlItem().setOnAction(a ->
                openUrl(table.getSelectionModel().getSelectedItem().getSearchUrlSold()));

        searchingBtn.setTooltip(new Tooltip("Start searching for items"));
        clearBtn.setTooltip(new Tooltip("Clear all results"));
        parentCategoryBtn.setTooltip(new Tooltip("Select parent category"));
        subcategoryBtn.setTooltip(new Tooltip("Select subcategory"));

        maxThreadsSpn.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 4));

        conditionCb.setItems(FXCollections.observableArrayList("All", "New", "Used"));
        conditionCb.setValue("All");

        searchingBtn.setDisable(false);
        stopBtn.setDisable(true);
    }

    @FXML
    private void startSearching() {
        stop();
        clearOutput();
        if (convertor != null) convertor.stop();

        if (queriesTa.getText() == null || queriesTa.getText().isEmpty()) {
            showAlert("Error", "Queries not specified");
            return;
        }
        List<String> queries = Arrays.asList(queriesTa.getText().split("\\r?\\n"));
        itemsSeeker = new ItemsSeeker(queries, appName, getCondition(), this);
        itemsSeeker.setLogger(this);
        itemsSeeker.setMaxThreads(maxThreadsSpn.getValue());
        //Items limit
        try {
            if (itemsLimitTf.getText() != null && itemsLimitTf.getText().length() > 0)
                itemsSeeker.setItemsLimit(Integer.parseInt(itemsLimitTf.getText()));
        } catch (NumberFormatException e) {
            showAlert("Error", "Incorrect items limit!");
            return;
        }
        //Category
        if (categoryIdTf.getText() != null && categoryIdTf.getText().length() > 0)
            itemsSeeker.setCategoryId(categoryIdTf.getText());

        log("--- Items searching started ---");
        stopBtn.setDisable(false);
        searchingBtn.setDisable(true);
        itemsSeeker.start();
    }

    @FXML
    private void stop() {
        if (itemsSeeker != null && itemsSeeker.isRunning()) itemsSeeker.stop();
    }

    @FXML
    private void clearAll() {
        stop();
        clearOutput();
        stopBtn.setDisable(true);
        searchingBtn.setDisable(false);
        queriesTa.setText("");
        upcTa.setText("");
        fullTitleTa.setText("");
    }

    private void clearOutput(){
        resultsSet.clear();
        results.clear();
        table.refresh();
    }

    @FXML
    private void selectSubcategory() {
        String categoryId = category.getChildren().get(categoryCb.getValue());
        if (categoryId != null) selectCategory(categoryId);
    }

    @FXML
    private void selectParentCategory() {
        if (category.getParentId() != null && !category.getParentId().equals("0")) selectCategory(category.getParentId());
    }

    private void selectCategory(String categoryId) {
        category = Category.findById(categoryId);
        if (category == null) {
            categoryIdTf.setText("-1");
            return;
        }
        categoryIdTf.setText(categoryId);
        categoryNameTf.setText(category.getName());
        categoryCb.getItems().clear();
        categoryCb.getItems().addAll(category.getChildren().keySet());
        if (!categoryCb.getItems().isEmpty()) categoryCb.setValue(categoryCb.getItems().get(0));
    }

    @Override
    public void onResultReceived(Result result) {
        if (!resultsSet.contains(result.getQuery())) {
            resultsSet.add(result.getQuery());
            results.add(result);
        }
        table.refresh();
    }

    @Override
    public void onAllResultsReceived() {
        log("--- Items searching completed ---");
        stopBtn.setDisable(true);
        searchingBtn.setDisable(false);
    }

    @Override
    public void log(String message) {
        String curTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
        Platform.runLater(() -> {
            consoleTa.setText(consoleTa.getText() + curTime + ": " + message +"\n");
            consoleTa.positionCaret(consoleTa.getLength());
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(null);
        alert.setGraphic(null);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private ItemsSeeker.Condition getCondition(){
        if (conditionCb.getValue().equals("New")) return ItemsSeeker.Condition.NEW;
        if (conditionCb.getValue().equals("Used")) return ItemsSeeker.Condition.USED;
        return ItemsSeeker.Condition.ALL;
    }

    @FXML
    private void convertUpcs(){
        if (upcTa.getText() == null || upcTa.getText().isEmpty()) {
            showAlert("Error", "UPCs not specified");
            return;
        }
        notFoundUpcs.clear();
        List<String> upcs = Arrays.stream(upcTa.getText().split("\\r?\\n"))
                .distinct()
                .filter(u -> u.length() > 0)
                .collect(Collectors.toList());
        convertor = new UpcConvertor(upcs, discogsToken, this);
        convertor.setLogger(this);
        fullTitleTa.setText("");
        log("UPCs conversion started");
        convertBtn.setDisable(true);
        convertor.start();

    }

    @Override
    public void onUpcConverted(String upc, Release release) {
        queriesTa.setText(queriesTa.getText()
                + (queriesTa.getText() == null || queriesTa.getText().isEmpty() ? "" : "\n")
                + release.getTitle());

        fullTitleTa.setText(fullTitleTa.getText()
                + (fullTitleTa.getText() == null || fullTitleTa.getText().isEmpty() ? "" : "\n")
                + release.toString()
        );
    }

    @Override
    public void onAllUpcConverted() {
        if (notFoundUpcs.isEmpty()) log("All UPCs converted");
        else
            log("UPCs conversion finished. The following UPCs were not found:\n"
                    + String.join( "\n", notFoundUpcs));
        convertBtn.setDisable(false);
    }

    @Override
    public void onUpcNotFound(String upc) {
        notFoundUpcs.add(upc);
    }

    private void openUrl(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            log("Failed to open URL");
            e.printStackTrace();
        }
    }
}
