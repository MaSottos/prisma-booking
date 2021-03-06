package com.prisma.prismabooking.service;

import com.google.gson.Gson;
import com.prisma.prismabooking.component.ConfigurationComponent;
import com.prisma.prismabooking.model.PagedResponse;
import com.prisma.prismabooking.utils.BadRequestException;
import com.prisma.prismabooking.utils.ConflictException;
import com.prisma.prismabooking.utils.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public abstract class BaseService<T> {

    protected ConfigurationComponent config;
    protected List<T> list = new ArrayList<>();
    protected Gson gson;
    protected Resource resource;
    protected String getterOfPrimaryKey;
    protected String nameOfClass;


    protected void init(Type obj, String pathFile) {
        nameOfClass=obj.getClass().getName();
        try {
            if (!Files.exists(Paths.get(config.getDataPath()))) {
                Files.createDirectory(Paths.get(config.getDataPath()));
            }
            File file = new File(config.getDataPath() + pathFile);
            if(file.createNewFile()){
                Files.write(file.toPath(), ("[").getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
            }
            ResourceLoader rl = new DefaultResourceLoader();
            resource = rl.getResource("file:" + config.getDataPath() + pathFile);
            if (resource.exists()) {
                Stream<String> lines = Files.lines(resource.getFile().toPath());
                lines.forEach(l -> {
                    l= l.replace("[{", "{");
                    l= l.replace("},", "}");
                    l= l.replace("}]", "}");
                    list.add(gson.fromJson(l, obj));
                });
            }
        } catch (IOException e) {
            throw new BadRequestException(String.format("Error in init of "+nameOfClass+" with cause: (IOException) %s", e.getMessage()));
        }
    }


    public T createNew(T obj) {
        if (!list.contains(obj)) {
            list.add(obj);
            updateJson();
            return obj;
        }
        throw new ConflictException("id", invokeGetMethod(obj).toString());
    }

    public T getSingle(String id) {
        return list.stream()
                .filter(Objects::nonNull)
                .filter(t -> invokeGetMethod(t).toString().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    public void deleteSingle(String id) {
        T objToReturn = list.stream()
                .filter(Objects::nonNull)
                .filter(t -> invokeGetMethod(t).toString().equalsIgnoreCase(id))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(id));
        list.remove(objToReturn);
        updateJson();
    }

    public T updateSingle(T obj, String id) {
        deleteSingle(id);
        createNew(obj);
        return obj;
    }

    protected PagedResponse<T> findPage(List<T> filteredList, Integer index, Integer limit) {
        List<T> pageContent;
        limit = handleLimit(limit);
        index = Optional.ofNullable(index).orElse(0);
        int start = index * limit;
        int end = start + limit;

        if (start > filteredList.size() || limit < 0 || start < 0) {
            if (start > filteredList.size() || start<0) {
                throw new BadRequestException("Offset "+index+" not valid");
            }
            throw new BadRequestException("Limit "+limit+" can't be negative");
        } else if (end > filteredList.size()) {
            end = filteredList.size();
            pageContent = filteredList.subList(start, filteredList.size());
        } else
            pageContent = filteredList.subList(start, end);

        if(pageContent.size()<=0){
            throw new NotFoundException();
        }
        return PagedResponse.<T>builder()
                .data(pageContent)
                .index(index + " of " + (int) Math.ceil(((double) filteredList.size() / (double) limit) - 1))
                .elements("from " + start + " to " + end)
                .totalElements((long) filteredList.size())
                .build();
    }

    private Object invokeGetMethod(T objOfMethod) {
        return Arrays.stream(objOfMethod.getClass().getMethods())
                .filter(method -> method.getName().contains(getterOfPrimaryKey))
                .map(method -> {
                    try {
                        return method.invoke(objOfMethod);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new BadRequestException(String.format("Error in invokeGetMethod of "+nameOfClass+" with cause: (IllegalAccessException | InvocationTargetException) {}", e.getMessage()));
                    }
                })
                .findFirst()
                .orElseThrow(()-> new NotFoundException("Primary key is null"));
    }

    public void updateJson(){
        String updatedList = list.stream()
                .map(t -> gson.toJson(t))
                .collect(Collectors.joining(","+System.lineSeparator()));
        try {
            Files.write(resource.getFile().toPath(), ("["+updatedList+"]"+System.lineSeparator()).getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new BadRequestException(String.format("Error in updateJson of "+nameOfClass+" with cause: (IOException) {}", e.getMessage()));
        }
    }

    private Integer handleLimit(Integer limit) {
        limit = Optional.ofNullable(limit)
                .filter(l -> l <= config.getMaxPageLimit())
                .orElse(config.getDefaultPageLimit());
        if (limit > list.size()) {
            limit = list.size();
        }
        return limit;
    }


}
