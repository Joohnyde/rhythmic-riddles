/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package com.cevapinxile.cestereg.persistence.repository;

import com.cevapinxile.cestereg.api.quiz.dto.response.CategorySimple;
import com.cevapinxile.cestereg.api.quiz.dto.response.LastCategory;
import com.cevapinxile.cestereg.persistence.entity.CategoryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author denijal
 */
public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    public List<CategorySimple> findByGameId(UUID gameId);

    public Integer findNextId(UUID gameId);

    public LastCategory findLastCategory(UUID gameId);

}
