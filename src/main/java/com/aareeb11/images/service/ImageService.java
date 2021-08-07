package com.aareeb11.images.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Aggregates.project;
import static com.mongodb.client.model.Filters.eq;

@Service
@Slf4j
public class ImageService {

    @Value("${application.bucket.name}")
    private String bucketName;

    @Value("${spring.data.mongodb.database}")
    private String database;

    @Autowired
    private AmazonS3 s3Client;

    private static final String PATTERN = "([^\\s]+(\\.(?i)(jpg|png|gif|bmp))$)";

    public String uploadImage(String email, MultipartFile file, String name, String username, int age, List<String> tags) {
        File fileObj = convertMultipartFileToFile(file);
        String fileName = file.getOriginalFilename();
        Pattern pattern = Pattern.compile(PATTERN);
        Matcher matcher = pattern.matcher(fileName.replaceAll("\\s", ""));
        if (!matcher.matches()) {
            return "Invalid file extension.";
        }
        s3Client.putObject(new PutObjectRequest(bucketName, email + "/" + fileName, fileObj));
        URL imageUrl = s3Client.getUrl(bucketName, email + "/" + fileName);
        fileObj.delete();

        return "" + imageUrl;
    }

    public String viewImages(String email, String tag) {
        MongoClient mongo = MongoClients.create();
        MongoDatabase db = mongo.getDatabase(database);
        MongoCollection<Document> collection = db.getCollection("users");
        Document doc = collection.aggregate(Arrays.asList(Aggregates.match(eq("_id", email)),
                project(Projections.computed("val", "$imageInfo." + tag + ".s3Urls")))).first();

        List<Document> imageUrls = (List<Document>) doc.get("val");
        return "Images successfully retrieved: " + imageUrls.get(0);
    }

    /*
    Delete:
    Option 1: Given email and image URLs from Postman, delete images from S3 and MongoDB (all instances in all tags)
    Option 2: Given email and tag, delete the tag
    */

    public String deleteImage(String email, List<String> URLs) {
        MongoClient mongo = MongoClients.create();
        MongoDatabase db = mongo.getDatabase(database);
        MongoCollection<Document> collection = db.getCollection("users");

        Document doc = collection.aggregate(Arrays.asList(Aggregates.match(eq("_id", email)),
                project(Projections.computed("val", "$imageInfo")))).first();
        List<Document> docs = (List<Document>) doc.get("val");
        List<String> tags = new ArrayList<>();

        for (Map.Entry<String, Object> set : docs.get(0).entrySet()) {
            System.err.format("%s%n", set.getKey());
            tags.add(set.getKey());
        }
        for (String URL: URLs) {
            for (String tag : tags) {
                Bson filter = eq("_id", email);
                Bson delete = Updates.pull("imageInfo.0." + tag + ".s3Urls", URL);
                collection.updateOne(filter, delete);
            }
        }

        return "Deleted URL " + URLs;
    }

    private File convertMultipartFileToFile(MultipartFile file) {
        File convertedFile = new File(file.getOriginalFilename());
        try (FileOutputStream fos = new FileOutputStream(convertedFile)) {
            fos.write(file.getBytes());
        } catch (IOException e) {
            log.error("Error converting MultipartFile to File", e);
        }
        return convertedFile;
    }
}
