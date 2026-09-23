package com.company.projectmanagement.identity.mapper;

import com.company.projectmanagement.identity.domain.AppUser;
import com.company.projectmanagement.identity.domain.UserStatus;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {

    @Select("SELECT count(*) FROM app_user")
    long countAllUsers();

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM app_user
                WHERE LOWER(username) = LOWER(#{username})
                  AND deleted_at IS NULL
            )
            """)
    boolean existsActiveUsername(@Param("username") String username);

    @Select("""
            SELECT EXISTS (
                SELECT 1 FROM app_user
                WHERE LOWER(email) = LOWER(#{email})
                  AND deleted_at IS NULL
            )
            """)
    boolean existsActiveEmail(@Param("email") String email);

    @Select("""
            SELECT *
            FROM app_user
            WHERE id = #{id}
              AND deleted_at IS NULL
            """)
    AppUser selectForAdministrationById(@Param("id") Long id);

    @Update("""
            UPDATE app_user
            SET status = #{status}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND deleted_at IS NULL
            """)
    int updateStatusForAdministration(
            @Param("id") Long id,
            @Param("status") UserStatus status);

    @Select("""
            <script>
            SELECT count(*)
            FROM app_user
            WHERE deleted_at IS NULL
            <if test='keyword != null'>
              AND (LOWER(username) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(display_name) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            <if test='status != null'>
              AND status = #{status}
            </if>
            </script>
            """)
    long countForAdministration(
            @Param("keyword") String keyword,
            @Param("status") UserStatus status);

    @Select("""
            <script>
            SELECT *
            FROM app_user
            WHERE deleted_at IS NULL
            <if test='keyword != null'>
              AND (LOWER(username) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
                   OR LOWER(display_name) LIKE LOWER(CONCAT('%', #{keyword}, '%')))
            </if>
            <if test='status != null'>
              AND status = #{status}
            </if>
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AppUser> selectForAdministration(
            @Param("keyword") String keyword,
            @Param("status") UserStatus status,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM app_user
            WHERE LOWER(username) = LOWER(#{username})
              AND status = 'ACTIVE'
              AND deleted_at IS NULL
            """)
    AppUser selectActiveByUsername(@Param("username") String username);

    @Select("""
            SELECT *
            FROM app_user
            WHERE id = #{id}
              AND status = 'ACTIVE'
              AND deleted_at IS NULL
            """)
    AppUser selectActiveById(@Param("id") Long id);
}
