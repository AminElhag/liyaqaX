package com.liyaqa.pt

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PTPackageCatalogRepository : JpaRepository<PTPackageCatalog, Long>
