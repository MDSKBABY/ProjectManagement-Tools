package com.company.projectmanagement.identity.mapper;

import com.company.projectmanagement.identity.domain.AppRole;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AppRoleMapper extends BaseMapper<AppRole> {

    @Select("""
            SELECT id
            FROM app_role
            WHERE code = #{code}
              AND deleted_at IS NULL
            """)
    Long selectActiveIdByCode(@Param("code") String code);
}
