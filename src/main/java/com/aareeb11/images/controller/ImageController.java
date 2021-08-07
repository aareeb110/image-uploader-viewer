package com.aareeb11.images.controller;

import com.aareeb11.images.service.ImageService;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;


@RestController
@RequestMapping("/image")
public class ImageController {

    @Autowired
    private ImageService service;

    @Value("${spring.data.mongodb.database}")
    private String database;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadImages(@RequestParam(value = "email") String email,
                                               @RequestParam(value = "files") MultipartFile[] files,
                                               @RequestParam(value = "name") String name,
                                               @RequestParam(value = "username") String username,
                                               @RequestParam(value = "age") int age,
                                               @RequestParam(value = "tag") List<String> tags) {
        List<String> fileNames = new ArrayList<>();
        List<String> imageUrls = new ArrayList<>();

        Arrays.asList(files).stream().forEach(file -> {
            imageUrls.add(service.uploadImage(email, file, name, username, age, tags));
            fileNames.add(file.getOriginalFilename());
        });

        for (String tag : tags) {
            MongoClient mongo = MongoClients.create();
            MongoDatabase db = mongo.getDatabase(database);
            MongoCollection<Document> collection = db.getCollection("users");
            Document doc = collection.find(eq("_id", email)).first();
            if (doc == null) {
                doc = new Document("_id", email);
                doc.append("name", name).append("username", username).append("age", age).append("imageInfo",
                        Arrays.asList((new Document(tag, new Document("s3Urls", imageUrls)))));
                collection.insertOne(doc);
            } else {
                Bson filter = eq("_id", email);
                for (String image : imageUrls) {
                    Bson change = Updates.addToSet("imageInfo.0." + tag + ".s3Urls", image);
                    collection.updateOne(filter, change);
                }
            }
        }

        return new ResponseEntity<>("Images uploaded successfully.", HttpStatus.OK);
    }

    @GetMapping("/view")
    public ResponseEntity<String> viewImages(@RequestParam(value = "email") String email,
                                             @RequestParam(value = "tag") String tag) {
        return new ResponseEntity<>(service.viewImages(email, tag), HttpStatus.OK);
    }

    @DeleteMapping({ "/delete" })
    public ResponseEntity<String> deleteImage(@RequestParam(value = "email") String email,
                                              @RequestParam(value = "URL") List<String> URLs) {
        return new ResponseEntity<>(service.deleteImage(email, URLs), HttpStatus.OK);
    }
}
